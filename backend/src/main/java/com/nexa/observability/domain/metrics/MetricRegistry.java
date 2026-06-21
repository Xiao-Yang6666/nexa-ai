package com.nexa.observability.domain.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * RED 指标注册表（聚合根，F-5010，零框架依赖、线程安全）。
 *
 * <p>可观测域核心聚合：在内存中累计「按渠道/模型维度」的 RED 指标（NFR-O01/O02），并产出导出快照。
 * RED 方法学三类：
 * <ul>
 *   <li><b>Rate</b>   —— {@code nexa_relay_requests_total{channel,model}}（counter，请求率由抓取方对 total 求速率）；</li>
 *   <li><b>Errors</b> —— {@code nexa_relay_errors_total{channel,model}}（counter，错误率 = errors/requests）；</li>
 *   <li><b>Duration</b>—— {@code nexa_relay_request_duration_seconds{channel,model}}（histogram，延迟分位）；</li>
 *   <li>额度速率 —— {@code nexa_relay_quota_spent_total{channel,model}}（counter，额度消耗速率）。</li>
 * </ul>
 * 业务行为（记一次请求/错误/延迟/额度）作为聚合根方法挂在本类上（充血模型，backend-engineer §2.2），
 * relay 链路埋点只调 {@code registry.recordRequest(...)}，不在外部散落累加逻辑。</p>
 *
 * <p>线程安全：counter 用 {@link LongAdder}/{@link DoubleAdder}（高并发累加优于 AtomicLong），gauge 用
 * {@code volatile} 包装，histogram 用每桶 {@link LongAdder}。{@link ConcurrentHashMap} 承载多序列。
 * relay 高并发埋点下不成为瓶颈（NFR-P01/P02 附加延迟约束）。仅依赖 JDK {@code java.util.concurrent}，
 * 非框架，可纯 JUnit 单测（DDD §2.1 domain 零框架依赖）。</p>
 *
 * <p>默认延迟分桶（秒）对齐网关延迟 SLO（NFR-P01 p50&lt;=15ms / p99&lt;=60ms）：覆盖毫秒到秒级。</p>
 */
public final class MetricRegistry {

    /** RED 标准指标名（namespace=nexa，subsystem=relay）。 */
    public static final String REQUESTS_TOTAL = "nexa_relay_requests_total";
    public static final String ERRORS_TOTAL = "nexa_relay_errors_total";
    public static final String QUOTA_SPENT_TOTAL = "nexa_relay_quota_spent_total";
    public static final String DURATION_SECONDS = "nexa_relay_request_duration_seconds";

    /** 默认延迟直方图桶上界（秒），对齐网关延迟 SLO（毫秒~秒级）。 */
    private static final double[] DEFAULT_LATENCY_BUCKETS =
            {0.005, 0.01, 0.015, 0.03, 0.06, 0.1, 0.25, 0.5, 1, 2.5, 5, 10};

    private final Map<MetricKey, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<MetricKey, GaugeCell> gauges = new ConcurrentHashMap<>();
    private final Map<MetricKey, Histogram> histograms = new ConcurrentHashMap<>();

    /**
     * 记一次中继请求（Rate + 可选 Errors + Duration + 额度，RED 一次性埋点）。
     *
     * <p>relay 链路在一笔请求结束时调用本方法，把请求计数、错误计数（若失败）、延迟观测、额度消耗一次性
     * 累计到对应渠道/模型维度序列。维度统一为 {@code channel} + {@code model}（NFR-O01 维度标签）。</p>
     *
     * @param channelLabel 渠道标识（如 channel id 字符串；未知用 "unknown"）
     * @param modelLabel   模型公开名 A（客户视图维度，不用上游名 B，避免泄露上游映射）
     * @param error        本次是否为错误请求（true 则 errors_total +1）
     * @param latencySec   请求延迟（秒）
     * @param quotaSpent   本次消耗额度（quota_sell 口径；&lt;=0 不累计）
     */
    public void recordRequest(String channelLabel, String modelLabel,
                              boolean error, double latencySec, long quotaSpent) {
        MetricLabels labels = MetricLabels.pair(
                "channel", safe(channelLabel), "model", safe(modelLabel));

        incCounter(MetricKey.of(REQUESTS_TOTAL, labels), 1);
        if (error) {
            incCounter(MetricKey.of(ERRORS_TOTAL, labels), 1);
        }
        if (quotaSpent > 0) {
            incCounter(MetricKey.of(QUOTA_SPENT_TOTAL, labels), quotaSpent);
        }
        observeHistogram(MetricKey.of(DURATION_SECONDS, labels), latencySec);
    }

    /**
     * 累加计数器（counter，单调递增）。
     *
     * @param key   序列身份键
     * @param delta 增量（必 &gt;= 0，counter 不可减）
     * @throws IllegalArgumentException delta 为负
     */
    public void incCounter(MetricKey key, long delta) {
        if (delta < 0) {
            // counter 单调递增不变量：负增量是埋点 bug，构造期即拒（不吞错，backend-engineer §3.2）。
            throw new IllegalArgumentException("counter delta must be non-negative, got " + delta);
        }
        counters.computeIfAbsent(key, k -> new LongAdder()).add(delta);
    }

    /**
     * 设置量规当前值（gauge，可增可减）。
     *
     * @param key   序列身份键
     * @param value 当前瞬时值
     */
    public void setGauge(MetricKey key, double value) {
        gauges.computeIfAbsent(key, k -> new GaugeCell()).value = value;
    }

    /**
     * 记一次直方图观测（histogram，落入对应累积桶 + 累计 sum/count）。
     *
     * @param key   序列身份键
     * @param value 观测值（如延迟秒数；负值按 0 处理避免污染分布）
     */
    public void observeHistogram(MetricKey key, double value) {
        histograms.computeIfAbsent(key, k -> new Histogram(DEFAULT_LATENCY_BUCKETS))
                .observe(Math.max(0d, value));
    }

    /**
     * 导出全部序列快照（不可变，供导出层渲染 Prometheus 文本）。
     *
     * @return 快照列表（counter + gauge + histogram，顺序：counter→gauge→histogram）
     */
    public List<MetricSnapshot> snapshot() {
        List<MetricSnapshot> out = new ArrayList<>();
        counters.forEach((key, adder) ->
                out.add(MetricSnapshot.scalar(key, MetricType.COUNTER, adder.sum())));
        gauges.forEach((key, cell) ->
                out.add(MetricSnapshot.scalar(key, MetricType.GAUGE, cell.value)));
        histograms.forEach((key, hist) -> out.add(hist.toSnapshot(key)));
        return out;
    }

    /** 空白维度兜底为 "unknown"（避免空标签值导致序列归并错乱）。 */
    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "unknown" : v;
    }

    /** 量规存储单元（volatile 保证可见性）。 */
    private static final class GaugeCell {
        private volatile double value;
    }

    /** 直方图存储单元（每桶 LongAdder + sum/count）。 */
    private static final class Histogram {
        private final double[] bounds;
        private final LongAdder[] bucketCounts;
        private final DoubleAdder sum = new DoubleAdder();
        private final LongAdder count = new LongAdder();

        Histogram(double[] bounds) {
            this.bounds = bounds;
            this.bucketCounts = new LongAdder[bounds.length + 1]; // +1 为 +Inf 桶
            for (int i = 0; i < bucketCounts.length; i++) {
                bucketCounts[i] = new LongAdder();
            }
        }

        void observe(double value) {
            sum.add(value);
            count.increment();
            // 落入第一个 value <= upperBound 的桶；都不满足则进 +Inf 桶（最后一个）。
            int idx = bounds.length; // 默认 +Inf 桶
            for (int i = 0; i < bounds.length; i++) {
                if (value <= bounds[i]) {
                    idx = i;
                    break;
                }
            }
            bucketCounts[idx].increment();
        }

        MetricSnapshot toSnapshot(MetricKey key) {
            List<MetricSnapshot.BucketCount> buckets = new ArrayList<>();
            long cumulative = 0;
            // Prometheus histogram 桶为累积计数（le 桶含所有更小观测）。
            for (int i = 0; i < bounds.length; i++) {
                cumulative += bucketCounts[i].sum();
                buckets.add(new MetricSnapshot.BucketCount(bounds[i], cumulative));
            }
            cumulative += bucketCounts[bounds.length].sum(); // +Inf 桶
            buckets.add(new MetricSnapshot.BucketCount(Double.POSITIVE_INFINITY, cumulative));
            return MetricSnapshot.histogram(key, buckets, sum.sum(), count.sum());
        }
    }
}
