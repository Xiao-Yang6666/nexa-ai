package com.nexa.channel.interfaces.api.dto;

/**
 * 渠道管理统一 API 响应信封（接口层，对齐全站 {@code {success, message, data}} 约定）。
 *
 * <p>与 com.nexa.deployment / com.nexa.account 的 ApiResponse 同构（各 bounded context 接口层各自持有
 * 信封 DTO，避免跨 context 耦合）。成功时 {@code success=true} 载 data；业务/参数错误时
 * {@code success=false} 载 message、{@code data=null}（API-ENDPOINTS 全站出参约定）。</p>
 *
 * @param <T>     data 载荷类型
 * @param success 是否成功
 * @param message 提示/错误信息（成功可空）
 * @param data    业务载荷（错误时为 null）
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    /**
     * 带消息的成功响应（无 data）。
     *
     * @param message 提示信息
     * @return 成功响应
     */
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /**
     * 带 data 载荷的成功响应。
     *
     * @param data 业务载荷
     * @param <T>  载荷类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> okData(T data) {
        return new ApiResponse<>(true, null, data);
    }

    /**
     * 带 message + data 的成功响应。
     *
     * @param message 提示信息
     * @param data    业务载荷
     * @param <T>     载荷类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> okData(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * 业务/集成错误响应（success=false）。
     *
     * @param message 错误信息
     * @return 错误响应
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
