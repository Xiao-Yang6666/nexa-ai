package com.nexa.telegram.domain.exception;

/**
 * Telegram 子域领域异常基类。
 *
 * <p>沿用 {@code com.nexa.account} 已定型的「每 bounded context 一个 DomainException 基类」风格
 * （backend-engineer §3.2 错误用明确类型不裸抛）。子类携带稳定语义，接口层
 * {@code TelegramExceptionHandler} 据具体子类翻译为 HTTP 状态码 + 错误信封。本类零框架依赖，
 * 与 domain 层其余类型一样可纯单测。</p>
 */
public abstract class DomainException extends RuntimeException {

    /**
     * @param message 面向用户/排障的错误描述（已设计为不泄露可枚举敏感信息）
     */
    protected DomainException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   底层错误（保留错误链，不吞错）
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
