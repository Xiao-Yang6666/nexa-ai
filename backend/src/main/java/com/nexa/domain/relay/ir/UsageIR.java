package com.nexa.domain.relay.ir;

/**
 * IR Usage（D5 token 用量，协议无关）。
 *
 * <p>领域规则来源：prd-relay.md RL-8 D5 + COMPAT-LAYER-DATA-OBJECTS §4.3。
 * 双向映射：
 * <pre>
 *   OpenAI    : usage.prompt_tokens   / usage.completion_tokens
 *   Anthropic : usage.input_tokens    / usage.output_tokens
 *   IR (统一)  : promptTokens         / completionTokens
 * </pre>
 * 计费 token 口径统一，供 RL-7 双价记账（{@code quota_sell/quota_cost}）。
 * {@code totalTokens} 缺省 = prompt + completion，序列化时若两协议提供不同总数应取上游原值。</p>
 *
 * @param promptTokens     入参 token（≥0）
 * @param completionTokens 出参 token（≥0）
 * @param totalTokens      合计 token（≥0；通常 = prompt+completion）
 */
public record UsageIR(int promptTokens, int completionTokens, int totalTokens) {

    public UsageIR {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("usage tokens must be >= 0");
        }
    }

    /** 默认 total = prompt+completion 的便捷构造。 */
    public static UsageIR of(int promptTokens, int completionTokens) {
        return new UsageIR(promptTokens, completionTokens, promptTokens + completionTokens);
    }

    /** 零值（计费缺失兜底，不抛异常）。 */
    public static final UsageIR ZERO = new UsageIR(0, 0, 0);
}
