package com.nexa.application.token;

import com.nexa.domain.token.repository.TokenRepository;
import com.nexa.domain.token.vo.Pagination;
import org.springframework.stereotype.Service;
import com.nexa.application.token.result.TokenPage;

/**
 * 令牌搜索用例（应用层，F-3003 GET /api/token/search）。
 *
 * <p>用例编排：仓储按 user_id + 关键词（name 模糊）强制过滤分页 + 计数 → 组装 {@link TokenPage}。
 * 空关键词等价该用户全量分页（归一在仓储层）。self-scope 由仓储层 SQL 兜底。薄编排，无领域规则。</p>
 */
@Service
public class SearchTokensUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public SearchTokensUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 关键词搜索某用户的令牌（F-3003）。
     *
     * @param actorUserId 归属用户 id（认证主体，self-scope）
     * @param keyword     关键词（可空白→全量）
     * @param pagination  分页参数
     * @return 令牌分页结果（items + total + 分页元数据）
     */
    public TokenPage search(long actorUserId, String keyword, Pagination pagination) {
        return new TokenPage(
                tokenRepository.searchByUser(actorUserId, keyword, pagination),
                tokenRepository.countSearchByUser(actorUserId, keyword),
                pagination.page(),
                pagination.pageSize());
    }
}
