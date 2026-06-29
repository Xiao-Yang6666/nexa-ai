package com.nexa.interfaces.growth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.domain.growth.vo.CheckinSetting;

/**
 * 签到配置管理端视图（接口层 DTO，对齐 openapi 签到配置回显，PRD GR-3 / F-1049）。
 *
 * @param enabled  是否启用签到
 * @param minQuota 最小奖励额度
 * @param maxQuota 最大奖励额度
 */
public record CheckinSettingVO(
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("min_quota") long minQuota,
        @JsonProperty("max_quota") long maxQuota) {

    /**
     * 由配置值对象组装管理端视图。
     *
     * @param setting 签到配置值对象
     * @return 管理端视图
     */
    public static CheckinSettingVO from(CheckinSetting setting) {
        return new CheckinSettingVO(setting.enabled(), setting.minQuota(), setting.maxQuota());
    }
}
