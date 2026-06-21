package com.nexa.growth.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 签到配置保存请求（接口层 DTO，管理端，PRD GR-3 / F-1049）。
 *
 * <p>入参校验（区间合法性）由领域值对象 {@code CheckinSetting} 在构造期守护，本 DTO 仅承载传输。
 * 包装类型允许字段缺省（缺省由用例/默认处理），布尔缺省视为 false。</p>
 *
 * @param enabled  是否启用（可空，缺省 false）
 * @param minQuota 最小奖励额度
 * @param maxQuota 最大奖励额度
 */
public record CheckinSettingRequest(
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("min_quota") Long minQuota,
        @JsonProperty("max_quota") Long maxQuota) {

    /** @return 启用开关（null 视为 false） */
    public boolean enabledOrFalse() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * @param fallback 缺省最小额度
     * @return min_quota（null 回落 fallback）
     */
    public long minQuotaOr(long fallback) {
        return minQuota == null ? fallback : minQuota;
    }

    /**
     * @param fallback 缺省最大额度
     * @return max_quota（null 回落 fallback）
     */
    public long maxQuotaOr(long fallback) {
        return maxQuota == null ? fallback : maxQuota;
    }
}
