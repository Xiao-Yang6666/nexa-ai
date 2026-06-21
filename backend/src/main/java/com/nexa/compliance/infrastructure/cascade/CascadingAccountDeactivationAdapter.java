package com.nexa.compliance.infrastructure.cascade;

import com.nexa.account.domain.model.OAuthBinding;
import com.nexa.account.domain.repository.OAuthBindingRepository;
import com.nexa.compliance.application.port.AccountDeactivationCascade;
import com.nexa.log.domain.repository.LogRepository;
import com.nexa.passkey.domain.repository.PasskeyCredentialRepository;
import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.repository.TokenRepository;
import com.nexa.token.domain.vo.Pagination;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 账号注销级联处置适配器（基础设施层，实现 {@link AccountDeactivationCascade}，F-5020）。
 *
 * <p>把 compliance 用例定义的「处置某用户全部关联数据」端口，落到各 bounded context 的现成仓储上
 * （依赖倒置 + 防腐：compliance 用例不直接耦合各 BC 仓储，只认 port，本 adapter 负责拼装，
 * backend-engineer §2.3）。处置遵循数据分级（DC-001）：凭证级删除、内容级匿名化、计量级保留。</p>
 *
 * <p>事务：本 adapter 的所有写操作在调用方用例（{@code DeactivateAccountUseCase.deactivate}）的
 * {@code @Transactional} 边界内执行，任一步失败整体回滚，杜绝半注销态。各步对「数据不存在」幂等。</p>
 *
 * <p>当前覆盖：令牌（token BC 软删）、OAuth 绑定（account BC 删除）、passkey（passkey BC 删除）、
 * 日志归属匿名化（log BC）。2FA：{@code com.nexa.twofa} 当前仅有领域模型、<b>尚无持久化仓储</b>，
 * 故 2FA 级联暂记 0 并留 TODO——待 twofa BC 落地 {@code TwoFARepository.deleteByUserId} 后在此补接。</p>
 */
@Component
public class CascadingAccountDeactivationAdapter implements AccountDeactivationCascade {

    /** 单次捞取待删令牌的页大小（注销用户令牌数有限，分页循环删尽，避免一次性全表）。 */
    private static final int TOKEN_PAGE_SIZE = Pagination.MAX_PAGE_SIZE;

    private final TokenRepository tokenRepository;
    private final OAuthBindingRepository oauthBindingRepository;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final LogRepository logRepository;

    /**
     * @param tokenRepository             令牌仓储（token BC）
     * @param oauthBindingRepository      OAuth 绑定仓储（account BC）
     * @param passkeyCredentialRepository passkey 凭据仓储（passkey BC）
     * @param logRepository               日志仓储（log BC，匿名化归属）
     */
    public CascadingAccountDeactivationAdapter(TokenRepository tokenRepository,
                                               OAuthBindingRepository oauthBindingRepository,
                                               PasskeyCredentialRepository passkeyCredentialRepository,
                                               LogRepository logRepository) {
        this.tokenRepository = tokenRepository;
        this.oauthBindingRepository = oauthBindingRepository;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.logRepository = logRepository;
    }

    @Override
    public CascadeResult purgeUserData(long userId, String username) {
        int tokensPurged = purgeTokens(userId);
        int oauthPurged = purgeOAuthBindings(userId);
        int passkeysPurged = purgePasskeys(userId);
        long logsAnonymized = anonymizeLogs(userId);
        // 2FA：twofa BC 暂无持久化仓储，待其提供 deleteByUserId 后补接（见类注释 TODO）。
        int twoFaPurged = 0;
        return new CascadeResult(tokensPurged, oauthPurged, passkeysPurged, twoFaPurged, logsAnonymized);
    }

    /**
     * 软删该用户名下所有令牌（凭证级，PURGE）。
     *
     * <p>分页捞取该用户全部令牌 id 后用 {@code softDeleteByUserAndIds} 批量软删（强制 self-scope，
     * 即便传入也只删本人）。循环直到捞不到更多（注销用户令牌数有限，通常一两批结束）。</p>
     *
     * @param userId 用户 id
     * @return 软删令牌数
     */
    private int purgeTokens(long userId) {
        int total = 0;
        Pagination page = Pagination.of(1, TOKEN_PAGE_SIZE);
        while (true) {
            List<Token> tokens = tokenRepository.findPageByUser(userId, page);
            if (tokens.isEmpty()) {
                break;
            }
            List<Long> ids = new ArrayList<>(tokens.size());
            for (Token t : tokens) {
                if (t.id() != null) {
                    ids.add(t.id());
                }
            }
            if (ids.isEmpty()) {
                break;
            }
            total += tokenRepository.softDeleteByUserAndIds(userId, ids);
            // 软删后这些令牌不再被 findPageByUser 返回（@SQLRestriction 过滤），始终查第 1 页即可。
            if (tokens.size() < TOKEN_PAGE_SIZE) {
                break;
            }
        }
        return total;
    }

    /**
     * 删除该用户全部 OAuth 第三方绑定（凭证级，PURGE）。
     *
     * @param userId 用户 id
     * @return 删除的绑定数
     */
    private int purgeOAuthBindings(long userId) {
        List<OAuthBinding> bindings = oauthBindingRepository.findByUserId(userId);
        int count = 0;
        for (OAuthBinding b : bindings) {
            oauthBindingRepository.delete(b);
            count++;
        }
        return count;
    }

    /**
     * 删除该用户的 passkey 凭据（凭证级，PURGE；幂等：无凭据时静默）。
     *
     * <p>passkey 仓储按 user_id 唯一（DB-SCHEMA §16），至多一条；删除前查是否存在以回报准确条数。</p>
     *
     * @param userId 用户 id
     * @return 删除的 passkey 数（0 或 1）
     */
    private int purgePasskeys(long userId) {
        int existed = passkeyCredentialRepository.findByUserId(userId).isPresent() ? 1 : 0;
        passkeyCredentialRepository.deleteByUserId(userId); // 幂等
        return existed;
    }

    /**
     * 匿名化该用户历史日志归属（内容/PII 级，ANONYMIZE；不删本体保留聚合）。
     *
     * @param userId 用户 id
     * @return 匿名化的日志条数
     */
    private long anonymizeLogs(long userId) {
        // 与用户聚合匿名占位一致的 username（deleted_<id>），解除日志与自然人的关联。
        String anon = "deleted_" + userId;
        return logRepository.anonymizeUserLogs(userId, anon);
    }
}
