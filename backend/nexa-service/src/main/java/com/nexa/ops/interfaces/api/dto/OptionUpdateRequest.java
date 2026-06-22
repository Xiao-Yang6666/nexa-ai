package com.nexa.ops.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 全站选项更新请求（接口层入参 DTO，F-4018 PUT /api/option/）。
 *
 * <p>对齐 API-ENDPOINTS §9.2 单键更新入参 {@code { key, value }}。值的领域校验（主题白名单/
 * 限流分组结构/合规键禁改）在 {@code OptionRegistry}（领域服务），本 DTO 仅承载。</p>
 *
 * @param key   配置键
 * @param value 配置值
 */
public record OptionUpdateRequest(
        @JsonProperty("key") String key,
        @JsonProperty("value") String value) {
}
