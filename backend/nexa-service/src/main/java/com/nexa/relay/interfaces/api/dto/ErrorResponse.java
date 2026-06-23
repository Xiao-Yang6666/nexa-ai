package com.nexa.relay.interfaces.api.dto;

/**
 * Relay 错误响应 DTO（对齐 openapi ErrorResponse + 各端点 4xx/5xx 返回结构）。
 *
 * <p>用于 Relay 端点的统一错误返回：{@code {error:{message, type, code}}}（OpenAI 兼容格式）。
 * Claude 格式错误返回另有结构（{@code {type:"error", error:{type, message}}}），在接口层按入站协议分支构造。</p>
 *
 * @param type    错误类型（如 invalid_request_error / server_error / authentication_error）
 * @param message 错误描述（脱敏，不含上游凭证）
 * @param code    稳定业务错误码（可空）
 */
public record ErrorResponse(String type, String message, String code) {

    public static ErrorResponse of(String type, String message, String code) {
        return new ErrorResponse(type, message, code);
    }

    public static ErrorResponse of(String type, String message) {
        return new ErrorResponse(type, message, null);
    }
}
