package com.nexa.log.domain.vo;

/**
 * 日志统计聚合结果值对象（F-4004 管理端 / F-4005 自助，仅 Type=2 Consume 口径）。
 *
 * <p>领域规则来源：prd 日志与用量 F-4004「返回 quota/rpm/tpm 三字段；仅统计 type=2(LogTypeConsume)」。
 * 由 {@link #of(long, long, long, long)} 从「区间内消费记录数、quota 总和、token 总和、区间秒数」
 * 计算派生指标，rpm/tpm 的换算（除以分钟）封装在值对象内，不散落到 SQL/接口层（充血计算）：
 * <ul>
 *   <li>{@code rpm} = 请求数 / 分钟数（requests per minute）；</li>
 *   <li>{@code tpm} = token 总数 / 分钟数（tokens per minute）；</li>
 *   <li>区间秒数 ≤ 0（未传时间窗）时分钟数兜底为 1，避免除零并保持「总量即速率」的可读语义。</li>
 * </ul>
 * </p>
 *
 * @param quota 区间内消费 quota 总和
 * @param rpm   每分钟请求数
 * @param tpm   每分钟 token 数
 */
public record LogStat(long quota, double rpm, double tpm) {

    /** 零统计（区间内无消费记录）。 */
    public static final LogStat ZERO = new LogStat(0L, 0.0, 0.0);

    /**
     * 由原始聚合量计算统计值对象。
     *
     * @param requestCount  区间内消费记录条数（仅 Type=2）
     * @param quotaSum      消费 quota 总和
     * @param tokenSum      消费 token 总和（prompt+completion）
     * @param windowSeconds 统计区间秒数（end-start；≤0 时分钟数兜底为 1）
     * @return 统计值对象
     */
    public static LogStat of(long requestCount, long quotaSum, long tokenSum, long windowSeconds) {
        // 分钟数：区间秒数/60，下限 1 分钟（避免除零，且短窗/无窗时 rpm≈总请求数，符合现网读数直觉）。
        double minutes = windowSeconds > 0 ? windowSeconds / 60.0 : 1.0;
        if (minutes < 1.0) {
            minutes = 1.0;
        }
        double rpm = requestCount / minutes;
        double tpm = tokenSum / minutes;
        return new LogStat(quotaSum, rpm, tpm);
    }
}
