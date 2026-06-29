package com.nexa.interfaces.account.provider.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型连通性测试请求 DTO（账号域，账号操作「测试」按钮）。
 *
 * <p>对已保存账号的指定模型发一次非流式聊天补全。apiKey 不在请求里——服务端从账号已存
 * credentials 解出（与 relay 真实转发取 key 一致），前端只传要测的模型与可选提示词。</p>
 *
 * @param model  要测试的模型 ID（必填）
 * @param prompt 测试提示词（可空 → 服务端回落默认短提示）
 */
public record TestModelRequest(
        @JsonProperty("model") String model,
        @JsonProperty("prompt") String prompt) {
}
