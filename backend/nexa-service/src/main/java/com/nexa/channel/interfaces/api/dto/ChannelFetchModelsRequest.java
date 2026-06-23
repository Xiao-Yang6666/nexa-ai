package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 按参数探测上游模型集请求 DTO（接口层，F-2026 新建渠道场景）。
 *
 * <p>新建渠道时渠道尚未保存、无 id，用表单的 type/base_url/key 直接探测上游 {@code /v1/models}。
 * key 为探测鉴权所需（多 Key 取首个）；base_url 可空（按 type 回落官方默认）。</p>
 *
 * @param type    渠道 type 码（必填）
 * @param baseUrl 上游 BaseURL（可空 → 按 type 回落）
 * @param key     上游 key（必填）
 */
public record ChannelFetchModelsRequest(
        @JsonProperty("type") Integer type,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("key") String key) {
}
