package com.nexa.log.infrastructure.token;

import com.nexa.log.application.port.TokenIdResolver;
import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.repository.TokenRepository;
import org.springframework.stereotype.Component;

/**
 * 令牌 id 解析适配器（基础设施层，实现 {@link TokenIdResolver}，F-4003）。
 *
 * <p>DDD 上下文解耦的落点：log BC 只依赖自己定义的 {@link TokenIdResolver} 端口，本适配器在基础设施层
 * 委托 token BC 的 {@link TokenRepository#findByKey} 完成 key→token_id 解析。这样 log 应用层/领域层不
 * 感知 token BC 的存在，跨 BC 引用收敛在基础设施边界（与 model BC 的 catalog adapter 同构）。</p>
 *
 * <p>key 缺失/空白/无效（查不到有效令牌）统一返回 0——契约 F-4003「token_id==0 = 无效的令牌」信号。
 * 不抛异常（让上层用例按 0 判定并给出契约文案），不回显 key（凭证不入日志/异常）。</p>
 */
@Component
public class TokenIdResolverAdapter implements TokenIdResolver {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储（token BC） */
    public TokenIdResolverAdapter(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    public long resolveTokenId(String key) {
        if (key == null || key.isBlank()) {
            return 0L;
        }
        return tokenRepository.findByKey(key.trim())
                .map(Token::id)
                .filter(id -> id != null && id > 0)
                .orElse(0L);
    }
}
