package com.nexa.publicsite.interfaces.api.dto;

/**
 * 统一 API 响应信封（公开站点子域，对齐 openapi.yaml {@code ApiResponse}）。
 *
 * <p>全站出参统一为 {@code {success, message, data}} 三段。各 bounded context 各持一份同形信封，
 * 避免跨 BC 编译耦合（与 account/telegram/log 一致）。</p>
 *
 * @param <T>     data 载荷类型
 * @param success 是否成功
 * @param message 提示/错误信息（成功可空）
 * @param data    业务载荷
 */
public record ApiResponse<T>(boolean success, String message, T data) {

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
}
