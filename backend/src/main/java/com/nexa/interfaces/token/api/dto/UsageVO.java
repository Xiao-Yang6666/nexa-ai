package com.nexa.interfaces.token.api.dto;

import com.nexa.domain.token.vo.UsageSummary;

/**
 * 令牌用量视图 DTO（接口层出参，对齐 openapi UsageCreditSummary，F-3012）。
 *
 * <p>由领域值对象 {@link UsageSummary} 直接映射（字段命名 snake_case 对齐 openapi）。</p>
 *
 * @param object              固定 "credit_summary"
 * @param total_granted       历史累计授予额度
 * @param total_used          已用额度
 * @param total_available     当前可用额度（无限额度=-1）
 * @param expires_at          过期时间 epoch 秒（永不过期归零为 0）
 * @param model_limits        允许模型 JSON 串
 * @param model_limits_enabled 是否启用模型限制
 */
public record UsageVO(
        String object,
        long total_granted,
        long total_used,
        long total_available,
        long expires_at,
        String model_limits,
        boolean model_limits_enabled) {

    /**
     * 从领域值对象映射。
     *
     * @param summary 用量摘要值对象
     * @return 用量视图 DTO
     */
    public static UsageVO from(UsageSummary summary) {
        return new UsageVO(
                summary.object(),
                summary.totalGranted(),
                summary.totalUsed(),
                summary.totalAvailable(),
                summary.expiresAt(),
                summary.modelLimits(),
                summary.modelLimitsEnabled());
    }
}
