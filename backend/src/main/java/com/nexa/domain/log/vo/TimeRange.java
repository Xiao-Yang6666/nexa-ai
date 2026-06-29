package com.nexa.domain.log.vo;

import com.nexa.domain.log.exception.InvalidLogQueryException;

/**
 * 查询时间区间值对象（F-4009 自助配额按日数据：跨度上限 1 个月护栏）。
 *
 * <p>领域规则来源：prd 日志与用量 F-4009「时间跨度超过 2592000 秒(1 月)返回『时间跨度不能超过 1 个月』」。
 * 把「1 个月跨度上限」作为<b>领域不变量</b>固化在值对象工厂里（充血护栏，backend-engineer §2.2），
 * 接口/用例不再散落 {@code if (end-start>2592000)} 裸比较；管理端无此上限，故只在自助路径用
 * {@link #boundedToOneMonth(Long, Long, long)} 构造。</p>
 *
 * @param start 起始 epoch 秒
 * @param end   结束 epoch 秒
 */
public record TimeRange(long start, long end) {

    /** 一个月的秒数上限（F-4009：2592000s = 30 天）。 */
    public static final long ONE_MONTH_SECONDS = 2_592_000L;

    /**
     * 构造自助配额查询区间，强制跨度 ≤ 1 个月（F-4009）。
     *
     * <p>缺省策略：end 缺省为 {@code now}；start 缺省为 {@code end - ONE_MONTH}（默认看近一个月）。
     * 显式传入但跨度超过 1 个月时抛 {@link InvalidLogQueryException}（文案对齐现网）。</p>
     *
     * @param start    原始起始（可空→end-1月）
     * @param end      原始结束（可空→now）
     * @param nowEpoch 当前 epoch 秒
     * @return 受 1 个月上限约束的时间区间
     * @throws InvalidLogQueryException 跨度超过 1 个月
     */
    public static TimeRange boundedToOneMonth(Long start, Long end, long nowEpoch) {
        long e = (end == null || end <= 0) ? nowEpoch : end;
        long s = (start == null || start <= 0) ? e - ONE_MONTH_SECONDS : start;
        if (e - s > ONE_MONTH_SECONDS) {
            // 文案与现网一致（中文），接口层直接透传不改写。
            throw new InvalidLogQueryException("时间跨度不能超过 1 个月");
        }
        return new TimeRange(s, e);
    }
}
