package com.nexa.log.domain.vo;

import com.nexa.log.domain.exception.InvalidLogQueryException;

/**
 * 排行榜统计周期值对象（F-4010 用量排行榜快照，period=week|month）。
 *
 * <p>领域规则来源：prd 日志与用量 F-4010「period 默认 week；非法 period 返回 400 invalid period」。
 * 周期不仅是标签，还携带「回看窗口秒数」{@link #lookbackSeconds()}——排行从 logs 表按
 * {@code created_at >= now - lookback} 的消费记录实时聚合（无快照表，DB-SCHEMA 未建 ranking 表，
 * 按窗口实时算）。</p>
 */
public enum Period {

    /** 近 7 天。 */
    WEEK("week", 7L * 24 * 3600),

    /** 近 30 天。 */
    MONTH("month", 30L * 24 * 3600);

    private final String wire;
    private final long lookbackSeconds;

    Period(String wire, long lookbackSeconds) {
        this.wire = wire;
        this.lookbackSeconds = lookbackSeconds;
    }

    /** @return 对外线格式值（week/month） */
    public String wireValue() {
        return wire;
    }

    /** @return 回看窗口秒数（用于 {@code created_at >= now - lookback} 实时聚合） */
    public long lookbackSeconds() {
        return lookbackSeconds;
    }

    /**
     * 由请求参数解析周期，缺省 week，非法值拒绝（F-4010 契约：非法 period → 400）。
     *
     * @param raw 原始 period 参数（可空/空白→week）
     * @return 周期值对象
     * @throws InvalidLogQueryException 当 raw 非 week/month 时（文案 "invalid period" 对齐契约）
     */
    public static Period parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEEK;
        }
        String v = raw.trim().toLowerCase();
        for (Period p : values()) {
            if (p.wire.equals(v)) {
                return p;
            }
        }
        throw new InvalidLogQueryException("invalid period");
    }
}
