package com.nexa.domain.security.exception;

/**
 * 非安全传输异常（HTTPS 强制）。
 *
 * <p>当请求经判定为明文 HTTP（非 TLS）到达需强制 HTTPS 的端点、且系统未配置为「重定向到 HTTPS」
 * 而是「直接拒绝」策略时抛出。对齐全站 HTTPS 强制要求（生产 server 为 {@code https://nexa.ai}）。</p>
 *
 * <p>映射 HTTP 400/403 系拒绝态；实际部署中绝大多数明文请求会先被中间件 308 重定向到 HTTPS，
 * 只有在 {@code reject} 策略下才会落到本异常。</p>
 */
public final class InsecureTransportException extends SecurityException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INSECURE_TRANSPORT";

    /**
     * @param message 拒绝原因（不含敏感内容）
     */
    public InsecureTransportException(String message) {
        super(CODE, message);
    }
}
