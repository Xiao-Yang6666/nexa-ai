package com.nexa.token.application;

import com.nexa.token.domain.repository.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 批量删除令牌用例（应用层，F-3007 POST /api/token/batch，软删）。
 *
 * <p>用例编排（事务边界）：仓储按 user_id + ids 强制 self-scope 批量软删除 → 返回实际删除条数。
 * 越权防护下沉到仓储层 SQL（{@code WHERE user_id=? AND id IN (...)}）——即便传入他人 id 也不会被删，
 * 故无需逐条加载校验。空 ids 返回 0。</p>
 */
@Service
public class BatchDeleteTokensUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public BatchDeleteTokensUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 批量软删除某用户的令牌（F-3007）。
     *
     * @param actorUserId 操作者用户 id（self-scope，仅删本人令牌）
     * @param ids         令牌 id 列表（可空→删 0 条）
     * @return 实际删除条数
     */
    @Transactional
    public int batchDelete(long actorUserId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return tokenRepository.softDeleteByUserAndIds(actorUserId, ids);
    }
}
