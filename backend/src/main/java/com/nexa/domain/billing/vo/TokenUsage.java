package com.nexa.domain.billing.vo;

import com.nexa.domain.billing.exception.InvalidBillingParameterException;

/**
 * Token 用量值对象 —— 单次请求的输入/输出 token 计数。
 *
 * <p>不可变、零框架依赖。承载 prd-billing BL-6 计费公式的输入项：{@code prompt_tokens}（输入）、
 * {@code completion_tokens}（输出）。等效输入 token 数 = {@code prompt + completion × completion_ratio}
 * （输出 token 经补全倍率放大后折算为等效输入，见 {@link BillingCalculator}）。</p>
 *
 * <p>不变量：token 计数非负。预扣阶段用估算 token，结算阶段用真实 token，二者均以本值对象承载。</p>
 *
 * @param promptTokens     输入 token 数（&gt;= 0）
 * @param completionTokens 输出 token 数（&gt;= 0）
 */
public record TokenUsage(long promptTokens, long completionTokens) {

    /**
     * 紧凑构造器：守护非负不变量（负 token 是上游解析 bug 的信号，构造期即拒，不吞错）。
     *
     * @throws InvalidBillingParameterException 当任一 token 计数为负时
     */
    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new InvalidBillingParameterException(
                    "token usage must be >= 0, got prompt=" + promptTokens + " completion=" + completionTokens);
        }
    }

    /**
     * 估算用量工厂：预扣阶段用单一估算 token 数（无输入/输出区分时，全部记为输入）。
     *
     * <p>prd-billing BL-6 §3 预扣阶段 {@code preConsumed = 估算token × (model_ratio × group_ratio)}
     * 走简化式，不区分输入输出（补全倍率在结算阶段才生效）。</p>
     *
     * @param estimatedTokens 估算 token 数
     * @return 用量值对象（completion=0）
     */
    public static TokenUsage estimate(long estimatedTokens) {
        return new TokenUsage(estimatedTokens, 0L);
    }
}
