package com.nexa.shared.security.domain.exception;

/**
 * 敏感数据加解密失败异常。
 *
 * <p>当敏感字段（可逆存储的密文，如渠道上游凭据/第三方 token）的对称加密或解密在密码学层面失败时抛出
 * （如密钥缺失/长度非法、密文被篡改导致 GCM 认证标签校验失败、Base64 结构损坏）。</p>
 *
 * <p>安全语义：解密失败可能意味着密文被篡改或密钥轮换不一致，属于不可恢复的完整性问题，向上传播让调用方
 * 决定降级/告警，<b>绝不</b>静默返回明文或空串（backend-engineer §3.2 不吞错）。message 不携带任何
 * 明文/密文/密钥片段，避免日志侧信道泄露。</p>
 */
public final class FieldEncryptionException extends SecurityException {

    /** 稳定业务错误码。 */
    public static final String CODE = "FIELD_ENCRYPTION_FAILED";

    /**
     * @param message 不含敏感内容的失败描述（如 "decrypt failed: authentication tag mismatch"）
     * @param cause   底层密码学异常（保留错误链便于排障，不外泄给客户）
     */
    public FieldEncryptionException(String message, Throwable cause) {
        super(CODE, message, cause);
    }

    /**
     * @param message 不含敏感内容的失败描述
     */
    public FieldEncryptionException(String message) {
        super(CODE, message);
    }
}
