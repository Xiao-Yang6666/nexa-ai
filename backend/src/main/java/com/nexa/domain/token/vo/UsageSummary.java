package com.nexa.domain.token.vo;

/**
 * 令牌用量摘要值对象（不可变，OpenAI 兼容 credit_summary，F-3012）。
 *
 * <p>领域规则来源：openapi UsageCreditSummary。由令牌聚合根依 remain_quota/used_quota/expired_time/
 * model_limits 派生计算（充血），接口层 {@code UsageVO} 直接映射本值对象字段。
 * 配额为整数额度单位（DB-SCHEMA §2），故用 long 承载。</p>
 *
 * <p>字段语义（对齐 openapi UsageCreditSummary）：
 * <ul>
 *   <li>{@code totalGranted} = remainQuota + usedQuota（历史累计授予）。</li>
 *   <li>{@code totalUsed} = usedQuota（已用）。</li>
 *   <li>{@code totalAvailable} = remainQuota（当前可用）；无限额度时与 granted/used 无意义，置 -1 标记无限。</li>
 *   <li>{@code expiresAt} = expiredTime；-1（永不过期）时按 openapi「ExpiredTime=-1 时归零」归零为 0。</li>
 * </ul></p>
 *
 * @param object             固定 {@code "credit_summary"}
 * @param totalGranted       历史累计授予额度（remain + used）
 * @param totalUsed          已用额度
 * @param totalAvailable     当前可用额度（无限额度=-1）
 * @param expiresAt          过期时间 epoch 秒（永不过期归零为 0）
 * @param unlimitedQuota     是否无限额度
 * @param modelLimits        允许模型 JSON 串（减法约束，可空）
 * @param modelLimitsEnabled 是否启用模型限制
 */
public record UsageSummary(
        String object,
        long totalGranted,
        long totalUsed,
        long totalAvailable,
        long expiresAt,
        boolean unlimitedQuota,
        String modelLimits,
        boolean modelLimitsEnabled) {

    /** credit_summary 对象类型常量。 */
    public static final String OBJECT_TYPE = "credit_summary";

    /** 无限额度时 available 的标记值。 */
    public static final long UNLIMITED_AVAILABLE = -1L;
}
