package com.nexa.domain.relay.exception;

import com.nexa.common.kernel.HttpAwareDomainException;

/**
 * 上游错误异常（502，复用 RL-3 错误处置链路）。
 *
 * <p>承载上游返回的状态码与脱敏后的错误内容，供 RL-3 重试判定 / 渠道禁用 / 日志记录使用。
 * message 已经过 MaskSensitiveErrorWithStatusCode 脱敏（不含 token key / 上游凭证）。</p>
 */
public class UpstreamException extends HttpAwareDomainException {

    private final int upstreamStatusCode;

    /**
     * @param upstreamStatusCode 上游返回的 HTTP 状态码（用于 ShouldRetryByStatusCode 判定）
     * @param message            脱敏后的错误描述
     */
    public UpstreamException(int upstreamStatusCode, String message) {
        super("UPSTREAM_ERROR", 502, message);
        this.upstreamStatusCode = upstreamStatusCode;
    }

    public UpstreamException(int upstreamStatusCode, String message, Throwable cause) {
        super("UPSTREAM_ERROR", 502, message, cause);
        this.upstreamStatusCode = upstreamStatusCode;
    }

    /** @return 上游 HTTP 状态码（驱动 RL-3 重试 / 禁用判定） */
    public int upstreamStatusCode() {
        return upstreamStatusCode;
    }
}
