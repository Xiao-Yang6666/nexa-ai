package com.nexa.domain.log.exception;

import com.nexa.domain.kernel.HttpAwareDomainException;

/**
 * 日志读侧持久化失败（→500，包装底层数据访问异常，不吞错保留错误链）。
 *
 * <p>仓储实现（{@code LogRepositoryImpl}）在查询/聚合/清理遇到数据访问异常时包装为本异常上抛，
 * 携带操作上下文（backend-engineer §3.2「错误必须 wrap 带上下文」），便于定位是哪一步出错；
 * 接口层翻译为 500 且不回显底层 message。</p>
 */
public class LogPersistenceException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "LOG_PERSISTENCE_ERROR";

    /**
     * @param message 操作上下文描述（如 "list logs by filter"）
     * @param cause   底层根因（DataAccessException 等）
     */
    public LogPersistenceException(String message, Throwable cause) {
        super(CODE, 500, message, cause);
    }
}
