package com.nexa.infrastructure.observability.trace;

import com.nexa.domain.observability.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪贯穿过滤器（基础设施层 / Web 适配，F-5012，横切中间件，无独立端点）。
 *
 * <p>在过滤链最前端为每个请求建立 {@link TraceContext}（入站→上游→结算贯穿，NFR-O04）：
 * <ol>
 *   <li>解析入站 W3C {@code traceparent} 头（或回退自定义 {@code X-Trace-Id}）续接上游链路；无则新建根上下文；</li>
 *   <li>写入 SLF4J {@link MDC}（{@code trace_id}/{@code span_id}）——本请求线程内所有结构化日志自动带 trace_id，
 *       实现日志层贯穿（relay 落 Log、审计、错误日志据此串联）；</li>
 *   <li>暴露到请求属性 {@link #TRACE_ATTR}，供 relay 链路调上游时透传 {@code traceparent}（→上游贯穿）；</li>
 *   <li>回写响应头 {@code X-Trace-Id} + W3C {@code traceparent}，便于客户端/APM 关联与 OTel 导出。</li>
 * </ol>
 * 请求结束<b>必</b>清理 MDC（finally），避免线程池复用导致 trace_id 串台（关键坑：线程复用脏 MDC）。</p>
 *
 * <p>Order 设为最高（在鉴权过滤器之前）——确保即使鉴权失败的请求也带 trace_id，便于排查未授权访问。
 * 本过滤器只做追踪上下文建立与传播，不做任何业务/鉴权判定（单一职责，backend-engineer §3.4）。</p>
 *
 * <p>TODO(W3+ OTel 导出接通): 当前在日志层（MDC）+ HTTP 头贯穿；接入 OpenTelemetry SDK 后，可在此基于
 * {@code traceparent} 起止 span 导出到 APM（NFR-O04 OTel 导出）。relay 调上游时读 {@link #TRACE_ATTR}
 * 注入上游请求头即完成「上游」段贯穿。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TracePropagationFilter extends OncePerRequestFilter {

    /** W3C Trace Context 标准入站/出站头。 */
    public static final String TRACEPARENT_HEADER = "traceparent";

    /** 自定义 trace id 头（回退 + 响应回写，便于客户端可读关联）。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 请求属性键：供 relay 链路取 TraceContext 透传上游。 */
    public static final String TRACE_ATTR = "com.nexa.observability.traceContext";

    /** MDC 键（结构化日志贯穿）。 */
    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_SPAN_ID = "span_id";

    /**
     * {@inheritDoc}
     *
     * <p>建立追踪上下文 → 写 MDC/请求属性/响应头 → 放行 → finally 清理 MDC。</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        TraceContext trace = resolveInbound(request);

        // 暴露给后续链路（relay 调上游透传 traceparent）。
        request.setAttribute(TRACE_ATTR, trace);

        // 日志层贯穿：本请求线程内所有日志自动带 trace_id/span_id。
        MDC.put(MDC_TRACE_ID, trace.traceId());
        MDC.put(MDC_SPAN_ID, trace.spanId());

        // 回写响应头：客户端/APM 可据此关联（OTel traceparent + 可读 X-Trace-Id）。
        response.setHeader(TRACEPARENT_HEADER, trace.toTraceParent());
        response.setHeader(TRACE_ID_HEADER, trace.traceId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 关键：线程池复用前必清 MDC，否则下一请求继承脏 trace_id（串台）。
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        }
    }

    /**
     * 据入站头建立追踪上下文：优先 W3C traceparent 续接，回退 X-Trace-Id，均无则新建。
     *
     * @param request HTTP 请求
     * @return 追踪上下文
     */
    private TraceContext resolveInbound(HttpServletRequest request) {
        String traceParent = request.getHeader(TRACEPARENT_HEADER);
        if (traceParent != null && !traceParent.isBlank()) {
            return TraceContext.fromTraceParent(traceParent);
        }
        // 回退：上游用自定义 X-Trace-Id 传递裸 traceId 时续接。
        String legacy = request.getHeader(TRACE_ID_HEADER);
        if (legacy != null && !legacy.isBlank()) {
            return TraceContext.continueTrace(legacy.trim());
        }
        return TraceContext.generate();
    }
}
