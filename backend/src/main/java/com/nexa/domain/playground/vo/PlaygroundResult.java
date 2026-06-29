package com.nexa.domain.playground.vo;

/**
 * Playground 试用执行结果（值对象，F-4038）。
 *
 * <p>承载站内试用经 relay 后的可下发结果：HTTP 状态码、是否流式、内容类型与原始响应字节。
 * Playground 透传上游 chat/completions 响应（流式 {@code text/event-stream} 或非流式 JSON），
 * 因此结果是<b>原样字节</b>而非结构化对象——避免在 Playground 域重复 relay 的协议转换逻辑
 * （复用 {@code RelayFormatOpenAI} 链路，API-ENDPOINTS §13 F-4038）。</p>
 *
 * <p>不可变、按值相等（值对象）。{@code body} 为 relay 已构造好的响应载荷（已计费、已落 Log、已脱敏）。</p>
 *
 * @param status      HTTP 状态码
 * @param stream      是否为流式响应（决定接口层 Content-Type）
 * @param contentType 响应内容类型（如 {@code application/json} / {@code text/event-stream}）
 * @param body        原始响应字节（透传上游，客户视图——不含成本/利润/上游模型 B）
 */
public record PlaygroundResult(int status, boolean stream, String contentType, byte[] body) {

    /**
     * 构造非流式 JSON 结果。
     *
     * @param status HTTP 状态码
     * @param body   JSON 响应字节
     * @return 结果值对象
     */
    public static PlaygroundResult json(int status, byte[] body) {
        return new PlaygroundResult(status, false, "application/json", body);
    }

    /**
     * 构造流式 SSE 结果。
     *
     * @param status HTTP 状态码
     * @param body   SSE 响应字节
     * @return 结果值对象
     */
    public static PlaygroundResult sse(int status, byte[] body) {
        return new PlaygroundResult(status, true, "text/event-stream", body);
    }
}
