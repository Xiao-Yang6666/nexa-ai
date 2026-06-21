package com.nexa.growth.domain.vo;

import com.nexa.growth.domain.exception.InvalidCheckinSettingException;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 签到配置值对象（不可变、自校验，PRD GR-3 / FC-022）。
 *
 * <p>承载签到功能开关与奖励额度区间（DB-SCHEMA §12 注：CheckinSetting 走 KV，默认
 * {@code enabled=false / min_quota=1000 / max_quota=10000}）。把「区间合法性」不变量封装在值对象
 * 构造期守护（backend-engineer §2.4 配额区间做值对象，避免裸字段散落 + 区间校验散落各处）：
 * <ul>
 *   <li>{@code minQuota <= maxQuota}（GR-3 S3 区间非法拒绝）</li>
 *   <li>{@code minQuota >= 0 且 maxQuota >= 0}（GR-3 S4 额度为负拒绝）</li>
 * </ul>
 * 违反即抛 {@link InvalidCheckinSettingException}（管理端保存接口据此拒绝落库）。</p>
 *
 * <p>领域规则来源：prd-growth.md GR-3、GR-1。</p>
 */
public final class CheckinSetting {

    /** 默认开关（DB-SCHEMA §12：默认关闭，须管理员显式开启）。 */
    public static final boolean DEFAULT_ENABLED = false;

    /** 默认最小奖励额度（DB-SCHEMA §12 min_quota 默认 1000）。 */
    public static final long DEFAULT_MIN_QUOTA = 1000L;

    /** 默认最大奖励额度（DB-SCHEMA §12 max_quota 默认 10000）。 */
    public static final long DEFAULT_MAX_QUOTA = 10000L;

    private final boolean enabled;
    private final long minQuota;
    private final long maxQuota;

    private CheckinSetting(boolean enabled, long minQuota, long maxQuota) {
        // 构造期守护区间不变量（GR-3 S3/S4）：先非负、再 min<=max，错误信息面向用户可读。
        if (minQuota < 0 || maxQuota < 0) {
            throw new InvalidCheckinSettingException("签到奖励额度不能为负");
        }
        if (minQuota > maxQuota) {
            throw new InvalidCheckinSettingException("最小额度不能大于最大额度");
        }
        this.enabled = enabled;
        this.minQuota = minQuota;
        this.maxQuota = maxQuota;
    }

    /**
     * 构造签到配置（带区间校验）。
     *
     * @param enabled  是否启用
     * @param minQuota 最小奖励额度（&gt;=0）
     * @param maxQuota 最大奖励额度（&gt;=0 且 &gt;= minQuota）
     * @return 合法的签到配置值对象
     * @throws InvalidCheckinSettingException 区间非法（负 / min&gt;max）
     */
    public static CheckinSetting of(boolean enabled, long minQuota, long maxQuota) {
        return new CheckinSetting(enabled, minQuota, maxQuota);
    }

    /**
     * 系统缺省配置（首次读取无 KV 记录时回落，DB-SCHEMA §12 默认值）。
     *
     * @return 缺省配置（关闭 / [1000,10000]）
     */
    public static CheckinSetting defaults() {
        return new CheckinSetting(DEFAULT_ENABLED, DEFAULT_MIN_QUOTA, DEFAULT_MAX_QUOTA);
    }

    /**
     * 在 {@code [minQuota, maxQuota]} 闭区间内随机抽取一次签到奖励额度（PRD GR-1 §4「发
     * [MinQuota,MaxQuota] 随机额度」）。
     *
     * <p>用 {@link ThreadLocalRandom} 取闭区间随机值（{@code nextLong(min, max+1)}）。当
     * {@code min == max} 时退化为定值。本方法是<b>领域行为</b>（奖励额度的产生规则属配置语义），
     * 放在值对象上而非散落到用例。</p>
     *
     * @return 本次签到应发放的随机额度（落在闭区间内）
     */
    public long drawReward() {
        if (minQuota == maxQuota) {
            return minQuota;
        }
        // nextLong 上界开区间，+1 使 maxQuota 可被取到（闭区间 [min,max]）。
        return ThreadLocalRandom.current().nextLong(minQuota, maxQuota + 1);
    }

    /** @return 是否启用签到 */
    public boolean enabled() {
        return enabled;
    }

    /** @return 最小奖励额度 */
    public long minQuota() {
        return minQuota;
    }

    /** @return 最大奖励额度 */
    public long maxQuota() {
        return maxQuota;
    }
}
