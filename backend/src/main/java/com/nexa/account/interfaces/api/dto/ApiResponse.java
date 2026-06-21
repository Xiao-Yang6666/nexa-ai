package com.nexa.account.interfaces.api.dto;

/**
 * 统一 API 响应信封（对齐 openapi.yaml {@code ApiResponse} / {@code ErrorResponse}）。
 *
 * <p>全站出参统一为 {@code {success, message, data}} 三段（API-ENDPOINTS 约定）。
 * 成功时 {@code success=true}、{@code data} 载具体载荷；业务/参数错误时 {@code success=false}、
 * {@code message} 载错误描述、{@code data=null}。</p>
 *
 * @param <T>     data 载荷类型
 * @param success 是否成功
 * @param message 提示/错误信息（成功可空）
 * @param data    业务载荷（错误时为 null）
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    /**
     * 构造仅含 success 的成功响应（无 data，对齐 SuccessResponse）。
     *
     * @return 成功响应
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 构造带消息的成功响应（无 data）。
     *
     * @param message 提示信息
     * @return 成功响应
     */
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /**
     * 构造带 data 载荷的成功响应。
     *
     * @param data 业务载荷
     * @param <T>  载荷类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> okData(T data) {
        return new ApiResponse<>(true, null, data);
    }

    /**
     * 构造业务错误响应（对齐 ErrorResponse，success=false）。
     *
     * @param message 错误信息
     * @return 错误响应
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
