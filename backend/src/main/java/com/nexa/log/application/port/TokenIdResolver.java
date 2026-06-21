package com.nexa.log.application.port;

/**
 * 令牌 key → token_id 解析端口（应用层出站端口，F-4003 tokenReadAuth）。
 *
 * <p>DDD 依赖倒置：log BC 的 F-4003「按令牌明文 key 查消费日志」需要把请求自带的明文 key 解析为
 * token_id，但 log BC 不应直接依赖 token BC 的仓储内部（上下文解耦）。故在此定义出站端口，由基础设施层
 * 适配器（{@code TokenIdResolverAdapter}）委托 token BC 的 {@code TokenRepository.findByKey} 实现。</p>
 *
 * <p>语义：key 无效/缺失（解析不出有效令牌）返回 0——契约 F-4003「token_id==0 返回『无效的令牌』」
 * 的统一信号，由用例 {@code QueryLogsByTokenUseCase} 判 0 抛 400。</p>
 */
public interface TokenIdResolver {

    /**
     * 把明文令牌 key 解析为 token_id。
     *
     * @param key 完整明文令牌 key（取自 Authorization 头；可空）
     * @return 命中令牌的 token_id；key 缺失/无效返回 0
     */
    long resolveTokenId(String key);
}
