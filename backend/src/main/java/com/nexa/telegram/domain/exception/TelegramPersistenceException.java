package com.nexa.telegram.domain.exception;

/**
 * Telegram 子域持久化异常（基础设施错误 wrap，不吞错）。
 *
 * <p>仓储实现层捕获底层数据访问异常后 wrap 为本类向上抛（保留错误链 cause），携带操作上下文，
 * 避免 Hibernate/JDBC 细节泄露到 domain/application（backend-engineer §3.2）。</p>
 */
public class TelegramPersistenceException extends DomainException {

    /**
     * @param message 操作上下文（如 "save telegram binding"）
     * @param cause   底层数据访问异常（保留错误链）
     */
    public TelegramPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
