package com.nexa.application.token;

import com.nexa.domain.token.exception.InvalidTokenParameterException;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量获取令牌明文 key 用例（应用层受控端点，F-3005 POST /api/token/keys/batch）。
 *
 * <p>用例编排：校验批量上限（≤100，对齐 openapi maxItems:100）→ 仓储按 user_id + ids 强制 self-scope
 * 查询（只返回归属本人的令牌）→ 组装 {@code id→key} 映射返回。越权 id 静默剔除（仓储层 SQL 只返本人，
 * 不抛 403 逐条报错——对齐 openapi「仅本人令牌」语义）。空 ids→空 map。</p>
 */
@Service
public class BatchRevealTokenKeysUseCase {

    /** 批量取明文 key 上限，对齐 openapi maxItems:100。 */
    public static final int MAX_BATCH_SIZE = 100;

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public BatchRevealTokenKeysUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 批量获取本人令牌的明文 key（F-3005）。
     *
     * @param actorUserId 操作者用户 id（self-scope，仅返回归属本人的令牌）
     * @param ids         令牌 id 列表（≤100，可空→空 map）
     * @return id→key 映射（仅命中的本人令牌）
     * @throws InvalidTokenParameterException ids 超过批量上限
     */
    public Map<Long, String> batchReveal(long actorUserId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new InvalidTokenParameterException("batch reveal exceeds max size " + MAX_BATCH_SIZE);
        }
        List<Token> tokens = tokenRepository.findByUserAndIds(actorUserId, ids);
        Map<Long, String> result = new LinkedHashMap<>(tokens.size());
        for (Token t : tokens) {
            result.put(t.id(), t.key());
        }
        return result;
    }
}
