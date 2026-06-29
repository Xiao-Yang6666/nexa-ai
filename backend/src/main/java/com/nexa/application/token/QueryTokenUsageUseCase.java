package com.nexa.application.token;

import com.nexa.domain.token.exception.InvalidTokenKeyException;
import com.nexa.domain.token.exception.TokenNotFoundException;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import com.nexa.domain.token.vo.UsageSummary;
import org.springframework.stereotype.Service;

/**
 * 令牌用量查询用例（应用层，F-3012 GET /api/usage/token）。
 *
 * <p>用例编排：按令牌 id 加载（令牌鉴权层负责把令牌 key 解析为 id 并确认权限）→ 由聚合充血方法
 * {@link Token#usageSummary()} 派生用量摘要（OpenAI 兼容 credit_summary）。缺失→404（接口层
 * 可能翻译为 401 令牌无效，依契约而定）。</p>
 *
 * <p><b>鉴权说明</b>：openapi 用 tokenReadAuth，即以令牌 key 鉴权。鉴权过滤器把令牌 key 解析为
 * 操作者（令牌 id + 归属用户）后调本用例，本用例不再校验 self-scope（用令牌访问自己的用量天然合法）。</p>
 */
@Service
public class QueryTokenUsageUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public QueryTokenUsageUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 查询令牌用量（F-3012）。
     *
     * @param tokenId 令牌 id（由令牌鉴权层解析 key 后给出）
     * @return 用量摘要（credit_summary，接口层直接映射为 UsageCreditSummary DTO）
     * @throws TokenNotFoundException 令牌不存在
     */
    public UsageSummary query(long tokenId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new TokenNotFoundException(tokenId));
        return token.usageSummary();
    }

    /**
     * 按令牌明文 key 查询用量（F-3012，tokenReadAuth：请求自带令牌 key）。
     *
     * <p>用令牌自身的 key 鉴权 + 查询自身用量天然合法，无需 self-scope 再校验。key 不存在/无效时
     * 抛 {@link InvalidTokenKeyException}，接口层翻译为 401（对齐 openapi UnauthorizedError）。</p>
     *
     * @param key 完整明文令牌 key（取自 Authorization 头）
     * @return 用量摘要
     * @throws InvalidTokenKeyException key 缺失或不匹配任何有效令牌
     */
    public UsageSummary queryByKey(String key) {
        Token token = tokenRepository.findByKey(key)
                .orElseThrow(InvalidTokenKeyException::new);
        return token.usageSummary();
    }
}
