package com.nexa.interfaces.api.account.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 探测上游模型列表请求 DTO（账号域，"获取模型列表"按钮）。
 *
 * <p>用新建/编辑表单当前填写的连接信息直接探测，无需先保存账号。
 * apiKey 为敏感凭证：仅用于本次探测请求鉴权，绝不落库、绝不回显。</p>
 *
 * @param platform 供应商平台（必填，决定协议规则）
 * @param baseUrl  Base URL（可空 → 按 platform 回落官方默认）
 * @param apiKey   API Key（必填，鉴权用）
 */
public record ProbeModelsRequest(
        @JsonProperty("platform") String platform,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key") String apiKey) {
}
