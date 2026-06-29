package com.nexa.application.token;

import com.nexa.domain.token.exception.TokenAccessDeniedException;
import com.nexa.domain.token.exception.TokenNotFoundException;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import org.springframework.stereotype.Service;

/**
 * 获取单个令牌明文 key 用例（应用层受控端点，F-3004 POST /api/token/{id}/key）。
 *
 * <p>用例编排：按 id 加载 → self-scope 校验（非本人→403）→ 取明文 key。明文 key 是凭证，仅本受控
 * 端点下发（限本人令牌）。缺失→404、越权→403。接口层直接返回 data=key 字符串。</p>
 */
@Service
public class RevealTokenKeyUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public RevealTokenKeyUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 获取单个令牌的明文 key（F-3004）。
     *
     * @param actorUserId 操作者用户 id（self-scope，必须是令牌归属者）
     * @param id          目标令牌 id
     * @return 完整明文 key
     * @throws TokenNotFoundException     令牌不存在
     * @throws TokenAccessDeniedException 越权访问他人令牌
     */
    public String reveal(long actorUserId, long id) {
        Token token = tokenRepository.findById(id)
                .orElseThrow(() -> new TokenNotFoundException(id));
        if (!token.belongsTo(actorUserId)) {
            throw new TokenAccessDeniedException(id);
        }
        return token.key();
    }
}
