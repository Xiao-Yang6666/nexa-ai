package com.nexa.application.token;

import com.nexa.domain.token.repository.TokenRepository;
import com.nexa.domain.token.vo.Pagination;
import org.springframework.stereotype.Service;

/**
 * 令牌列表查询用例（应用层，F-3002 GET /api/token/）。
 *
 * <p>用例编排：仓储按 user_id 强制过滤分页 + 计数 → 组装 {@link TokenPage}。
 * self-scope 由仓储层 SQL 兜底（只查本人令牌，ROLE-PERMISSION-MATRIX §3）。薄编排，无领域规则。</p>
 */
@Service
public class ListTokensUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public ListTokensUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 分页列出某用户的令牌（F-3002）。
     *
     * @param actorUserId 归属用户 id（认证主体，self-scope）
     * @param pagination  分页参数
     * @return 令牌分页结果（items + total + 分页元数据）
     */
    public TokenPage list(long actorUserId, Pagination pagination) {
        return new TokenPage(
                tokenRepository.findPageByUser(actorUserId, pagination),
                tokenRepository.countByUser(actorUserId),
                pagination.page(),
                pagination.pageSize());
    }
}
