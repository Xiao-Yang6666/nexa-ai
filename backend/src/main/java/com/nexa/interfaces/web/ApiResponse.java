package com.nexa.interfaces.web;

/**
 * 全站统一 API 响应信封（对齐 openapi.yaml {@code ApiResponse} / {@code ErrorResponse}）。
 *
 * <p>全站出参统一为 {@code {success, message, data}} 三段（API-ENDPOINTS 约定）。成功时
 * {@code success=true}、{@code data} 载具体载荷；业务/参数错误时 {@code success=false}、
 * {@code message} 载错误描述、{@code data=null}。字段经全局 Jackson {@code SNAKE_CASE}
 * （application.yml）序列化。</p>
 *
 * <p>本类是<b>协议构件</b>而非领域构件：信封形态由 HTTP API 契约决定，与任何 bounded context 的
 * 业务语义无关，故收敛到 {@code shared.web} 供全部 context 接口层复用（消除原各模块各自复制的 16 份
 * 同构信封带来的漂移风险）。</p>
 *
 * @param <T>     data 载荷类型
 * @param success 是否成功
 * @param message 提示/错误信息（成功可空）
 * @param data    业务载荷（错误时为 null）
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    /**
     * 仅含 success 的成功响应（无 data，对齐 SuccessResponse）。
     *
     * @return 成功响应
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

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
     * 业务/集成错误响应（对齐 ErrorResponse，success=false，data=null）。
     *
     * @param message 错误信息
     * @return 错误响应
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
