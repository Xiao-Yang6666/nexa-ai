package com.nexa.relay.interfaces.api.dto;

/**
 * Relay 统一 API 响应信封（接口层，对齐全站 {@code {success, message, data}} 约定）。
 *
 * <p>与 com.nexa.channel / com.nexa.routing 的 ApiResponse 同构（各 bounded context 接口层各自持有
 * 信封 DTO，避免跨 context 耦合）。</p>
 *
 * @param <T>     data 载荷类型
 * @param success 是否成功
 * @param message 提示/错误信息
 * @param data    业务载荷
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static ApiResponse<Void> ok(String message) { return new ApiResponse<>(true, message, null); }
    public static <T> ApiResponse<T> okData(T data) { return new ApiResponse<>(true, null, data); }
    public static ApiResponse<Void> error(String message) { return new ApiResponse<>(false, message, null); }
}
