package com.nexa.observability.domain.trace;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 链路追踪上下文值对象（F-5012，充血——自带生成/校验/W3C traceparent 编织）。
 *
 * <p>承载一笔请求的端到端追踪标识：{@code traceId}（32 hex，128bit，W3C Trace Context 规范）+
 * {@code spanId}（16 hex，64bit）。贯穿「入站→上游→结算」全链路（NFR-O04），并支持 OTel 导出
 * （{@link #toTraceParent()} 产出 W3C {@code traceparent} 头值）。不可变、按值相等（值对象）。</p>
 *
 * <p>核心行为：
 * <ul>
 *   <li>{@link #generate()} —— 入站无上游 trace 时新建根上下文；</li>
 *   <li>{@link #fromTraceParent(String)} —— 解析上游传入的 W3C {@code traceparent}，复用其 traceId
 *       续接链路（跨服务贯穿），解析失败则新建（不因坏头中断追踪）；</li>
 *   <li>{@link #toTraceParent()} —— 渲染为 W3C {@code traceparent}（传给上游/导出 OTel）。</li>
 * </ul>
 * 生成/校验规则在值对象上（充血，backend-engineer §2.2），过滤器只调 {@code TraceContext.generate()}，
 * 不在外部散落 hex 拼接。零框架依赖，可纯 JUnit 单测。</p>
 *
 * <p>领域规则来源：NFR-O04「入站→上游→结算贯穿 trace_id 支持 OTel 导出」；W3C Trace Context。</p>
 */
public final class TraceContext {

    /** W3C 规范：traceId 32 位 hex（不可全 0），spanId 16 位 hex（不可全 0）。 */
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");
    private static final String ZERO_TRACE = "00000000000000000000000000000000";
    private static final String ZERO_SPAN = "0000000000000000";

    /** W3C traceparent 版本（00）+ 采样标志（01=sampled）。 */
    private static final String VERSION = "00";
    private static final String SAMPLED_FLAG = "01";

    /** 线程安全的随机源（SecureRandom 可并发 nextBytes）。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String traceId;
    private final String spanId;

    private TraceContext(String traceId, String spanId) {
        this.traceId = traceId;
        this.spanId = spanId;
    }

    /**
     * 新建根追踪上下文（入站无上游 trace 时）。
     *
     * @return 新生成的上下文（随机 traceId + spanId）
     */
    public static TraceContext generate() {
        return new TraceContext(randomHex(16), randomHex(8));
    }

    /**
     * 以给定 traceId 续接链路、新建子 span（用于跨服务贯穿同一 trace）。
     *
     * @param traceId 上游 traceId（32 hex，非法则忽略并新建根上下文）
     * @return 续接上下文
     */
    public static TraceContext continueTrace(String traceId) {
        if (traceId == null || !TRACE_ID.matcher(traceId).matches() || ZERO_TRACE.equals(traceId)) {
            return generate();
        }
        return new TraceContext(traceId, randomHex(8));
    }

    /**
     * 解析 W3C {@code traceparent} 头，复用其 traceId 续接链路。
     *
     * <p>格式：{@code version-traceId-spanId-flags}（如 {@code 00-<32hex>-<16hex>-01}）。解析失败/缺失
     * 则新建根上下文——坏头不应中断追踪（健壮性优先，NFR-O04 端到端贯穿）。</p>
     *
     * @param traceParent W3C traceparent 头值（可空）
     * @return 续接或新建的上下文
     */
    public static TraceContext fromTraceParent(String traceParent) {
        if (traceParent == null || traceParent.isBlank()) {
            return generate();
        }
        String[] parts = traceParent.trim().split("-");
        // 至少 version-traceId-spanId-flags 四段；取 traceId（第 2 段）续接。
        if (parts.length >= 3 && TRACE_ID.matcher(parts[1]).matches() && !ZERO_TRACE.equals(parts[1])) {
            return continueTrace(parts[1]);
        }
        return generate();
    }

    /**
     * 渲染为 W3C {@code traceparent} 头值（传给上游 / 导出 OTel）。
     *
     * @return {@code 00-<traceId>-<spanId>-01}
     */
    public String toTraceParent() {
        return VERSION + "-" + traceId + "-" + spanId + "-" + SAMPLED_FLAG;
    }

    /** @return 128bit trace id（32 hex），落 Log 的 trace_id 字段、跨链路贯穿键 */
    public String traceId() {
        return traceId;
    }

    /** @return 64bit span id（16 hex），本跳 span 标识 */
    public String spanId() {
        return spanId;
    }

    /**
     * 生成 {@code byteLen*2} 位随机 hex 字符串（线程安全）。
     *
     * @param byteLen 字节数（traceId=16→32hex，spanId=8→16hex）
     * @return 小写 hex 串
     */
    private static String randomHex(int byteLen) {
        byte[] bytes = new byte[byteLen];
        RANDOM.nextBytes(bytes);
        char[] out = new char[byteLen * 2];
        for (int i = 0; i < byteLen; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        String s = new String(out);
        // 极小概率全 0（非法 traceId/spanId），重抽一次兜底不变量。
        if ((byteLen == 16 && ZERO_TRACE.equals(s)) || (byteLen == 8 && ZERO_SPAN.equals(s))) {
            return randomHex(byteLen);
        }
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TraceContext other)) return false;
        return traceId.equals(other.traceId) && spanId.equals(other.spanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId);
    }

    @Override
    public String toString() {
        return toTraceParent();
    }
}
