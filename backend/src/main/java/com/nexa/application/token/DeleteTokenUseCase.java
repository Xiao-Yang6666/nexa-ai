package com.nexa.application.token;

import com.nexa.domain.token.exception.TokenAccessDeniedException;
import com.nexa.domain.token.exception.TokenNotFoundException;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除单个令牌用例（应用层，F-3007 DELETE /api/token/{id}，软删）。
 *
 * <p>用例编排（事务边界）：按 id 加载 → self-scope 校验（非本人→403）→ 仓储软删除（写 deleted_at）。
 * 缺失→404、越权→403 由领域异常表达，接口层翻译。软删保留行（与 DB-SCHEMA §2 软删除语义一致）。</p>
 */
@Service
public class DeleteTokenUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public DeleteTokenUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 软删除单个令牌（F-3007）。
     *
     * @param actorUserId 操作者用户 id（self-scope）
     * @param id          目标令牌 id
     * @throws TokenNotFoundException     令牌不存在
     * @throws TokenAccessDeniedException 越权删除他人令牌
     */
    @Transactional
    public void delete(long actorUserId, long id) {
        Token token = tokenRepository.findById(id)
                .orElseThrow(() -> new TokenNotFoundException(id));
        if (!token.belongsTo(actorUserId)) {
            throw new TokenAccessDeniedException(id);
        }
        tokenRepository.softDeleteById(id);
        // 写穿失效：删除后立即清鉴权缓存，被删 token 不再因缓存命中而通过 /v1/* 鉴权（T12/CR-05）。
        tokenRepository.evictAuthCache(token.key());
    }
}
