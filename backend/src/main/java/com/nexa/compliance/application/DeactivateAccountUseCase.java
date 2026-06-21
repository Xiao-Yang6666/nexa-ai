package com.nexa.compliance.application;

import com.nexa.account.domain.exception.UserNotFoundException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.compliance.application.port.AccountDeactivationCascade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号注销用例（应用服务，F-5020 账号注销级联删除，DC-003/DC-011）。
 *
 * <p>编排「本人注销账号」的事务边界（self-scope，对齐 API-ENDPOINTS §14.5 F-5020 建议端点
 * {@code DELETE /api/user/self}）。本类<b>薄</b>，只负责事务编排与跨 BC 协调，业务规则在领域层：
 * <ol>
 *   <li>定位本人用户聚合（不存在 → {@link UserNotFoundException}）；</li>
 *   <li>调用聚合根充血方法 {@link User#anonymize()} 做 PII 不可逆匿名化（用户名占位、清空邮箱/展示名/备注/设置、
 *       密码置不可登录占位、状态禁用）；</li>
 *   <li>持久化匿名化后的聚合 + 仓储软删除（写 deleted_at，使该用户从所有查询消失）；</li>
 *   <li>经 {@link AccountDeactivationCascade} 级联处置其余 BC 的关联数据（令牌/OAuth 绑定/passkey/2FA 删除、
 *       日志归属匿名化）。</li>
 * </ol>
 * 全程在<b>单一事务</b>内（{@code @Transactional}）：任一步失败整体回滚，杜绝「半注销」态
 * （如 PII 已清但令牌残留可被滥用）。注销后 PII 清空或匿名（验收标准），凭证级数据物理删除。</p>
 *
 * <p>self-scope 鉴权（只能注销本人）在接口层用 {@code AuthenticatedActor} 保证：command 的 userId
 * 恒取自会话本人，不接受外部任意 id（详见 {@link DeactivateAccountCommand}）。</p>
 */
@Service
public class DeactivateAccountUseCase {

    private final UserRepository userRepository;
    private final AccountDeactivationCascade cascade;

    /**
     * @param userRepository 用户聚合仓储（account BC，匿名化保存 + 软删除）
     * @param cascade        跨 BC 级联处置端口（infra adapter 注入各 BC repository 实现）
     */
    public DeactivateAccountUseCase(UserRepository userRepository,
                                    AccountDeactivationCascade cascade) {
        this.userRepository = userRepository;
        this.cascade = cascade;
    }

    /**
     * 执行本人账号注销（级联匿名化 + 删除）。
     *
     * @param command 注销命令（userId = 本人）
     * @return 级联处置结果摘要（仅条数，无 PII），供接口层回执 / 审计
     * @throws UserNotFoundException 目标用户不存在（404；含已注销——软删后再注销不再命中）
     */
    @Transactional
    public AccountDeactivationCascade.CascadeResult deactivate(DeactivateAccountCommand command) {
        // 1. 定位本人聚合。软删除用户由 @SQLRestriction 过滤，已注销账号此处即查不到 → 幂等地报 404。
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        // 注销前留存原用户名，供日志归属匿名化精确定位（处置后聚合用户名已被占位覆盖）。
        String originalUsername = user.username() == null ? null : user.username().value();

        // 2. 领域层做 PII 不可逆匿名化（充血，规则在聚合内）。
        user.anonymize();

        // 3. 持久化匿名化结果 + 软删除（写 deleted_at，使其从所有查询消失，双保险）。
        userRepository.save(user);
        userRepository.softDelete(command.userId());

        // 4. 级联处置其余 BC 的关联数据（同一事务，失败整体回滚）。
        return cascade.purgeUserData(command.userId(), originalUsername);
    }
}
