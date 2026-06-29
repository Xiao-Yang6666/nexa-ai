package com.nexa.domain.model.exception;

import com.nexa.common.kernel.DomainException;

/**
 * 上游模型同步集成异常（接口层映射 502）。
 *
 * <p>上游 basellm 元数据拉取（F-3019/F-3020）连接失败/返回错误/解析失败时抛出。
 * 用 502 表达「本服务正常但上游故障」，区别于 4xx 客户端错误。message 不含上游凭证。</p>
 */
public class UpstreamSyncException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "UPSTREAM_SYNC_FAILED";

    /**
     * @param message 可读错误描述（不含凭证）
     */
    public UpstreamSyncException(String message) {
        super(CODE, message);
    }

    /**
     * 保留错误链的构造（wrap 上游异常，backend-engineer §3.2 不吞错）。
     *
     * @param message 可读错误描述
     * @param cause   原始异常
     */
    public UpstreamSyncException(String message, Throwable cause) {
        super(CODE, message);
        initCause(cause);
    }
}
