package com.nexa.nfr.domain.vo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 日志归档与分区策略（值对象，不可变）——F-5015 日志归档与分区，NFR-E03/DC-006。
 *
 * <p>定义「热数据保留期 + 超期归档/分区」策略并提供分区键与超期判定。领域规则来源：
 * BACKLOG T-219 F-5015 验收「超期日志按策略归档不影响查询」、API-ENDPOINTS §14.4 F-5015
 * （日志表分区+归档，基础设施；清理复用模块八 {@code DELETE /api/log/} F-4006）。横切落地：
 * 日志表按时间分区（PG 分区表），超期分区搬冷存/归档而非影响在线查询，本 BC 不挂端点。</p>
 *
 * <p>与 {@code com.nexa.compliance.domain.vo.PromptRetentionPolicy} 协同：留存策略管「正文是否留、留多久」
 * （合规视角，DC-005），本归档策略管「日志整体热数据保留与分区归档」（性能/成本视角，NFR-E03）。
 * 两者独立——正文可能更早被清，但元数据日志按归档策略保留更久供审计聚合。</p>
 *
 * @param hotRetentionDays 热数据（在线可查）保留天数（&gt;0，默认 90）
 */
public record LogArchivalPolicy(int hotRetentionDays) {

    /** 默认热数据保留天数（在线查询窗口）。 */
    public static final int DEFAULT_HOT_RETENTION_DAYS = 90;

    /**
     * 紧凑构造校验：保留天数为正。
     *
     * @throws IllegalArgumentException 非正
     */
    public LogArchivalPolicy {
        if (hotRetentionDays <= 0) {
            throw new IllegalArgumentException("hot retention days must be positive");
        }
    }

    /**
     * 契约默认策略。
     *
     * @return 默认归档策略
     */
    public static LogArchivalPolicy contractDefault() {
        return new LogArchivalPolicy(DEFAULT_HOT_RETENTION_DAYS);
    }

    /**
     * 计算某条日志（按写入时刻）所属的月度分区键（{@code YYYYMM}）。
     *
     * <p>按自然月分区是日志表分区的常用粒度：超期整月分区可整体搬冷存/detach，不逐行删，
     * 不影响在线分区的查询性能（F-5015「归档不影响查询」）。</p>
     *
     * @param recordedAtEpochSec 日志写入时刻 epoch 秒（&gt;=0）
     * @return 月度分区键，如 {@code "202606"}
     */
    public String partitionKey(long recordedAtEpochSec) {
        java.time.LocalDate d = Instant.ofEpochSecond(Math.max(recordedAtEpochSec, 0))
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return String.format("%04d%02d", d.getYear(), d.getMonthValue());
    }

    /**
     * 判断某条日志是否已超出热数据保留期（应被归档/搬冷存）。
     *
     * @param recordedAt 日志写入时刻
     * @param now        当前时刻
     * @return 超期（应归档）返回 {@code true}
     */
    public boolean shouldArchive(Instant recordedAt, Instant now) {
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(now, "now");
        return Duration.between(recordedAt, now).compareTo(Duration.ofDays(hotRetentionDays)) > 0;
    }
}
