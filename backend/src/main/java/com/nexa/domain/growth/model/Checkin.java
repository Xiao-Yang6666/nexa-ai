package com.nexa.domain.growth.model;

import com.nexa.domain.growth.vo.CheckinDate;

import java.util.Objects;

/**
 * 签到记录聚合根（充血领域模型，PRD GR-1 / DB-SCHEMA §12 {@code checkins}）。
 *
 * <p>增长子域「每日签到」的一致性边界。一条 Checkin 代表「某用户在某一天领取了一次随机额度奖励」，
 * 其不变量（复合唯一 {@code (user_id, checkin_date)}）由数据库唯一索引 {@code idx_user_checkin_date}
 * 在持久化层兜底守护，聚合在此保证「奖励额度非负、归属/日期非空」。</p>
 *
 * <p>充血行为（backend-engineer §2.2）：签到记录由聚合根工厂 {@link #create} 产生——在此即时绑定
 * 用户、日期、随机奖励额度与创建时间，应用层不在 service 里散落装配逻辑。本类零框架依赖
 * （不 import JPA/Spring），可纯单测。</p>
 *
 * <p>领域规则来源：prd-growth.md GR-1（每日签到状态机）；字段对齐 DB-SCHEMA §12 Checkin。</p>
 */
public class Checkin {

    /** 自增主键，未持久化的新记录为 null。 */
    private Long id;

    /** 归属用户 id（DB-SCHEMA §12 user_id not null，复合唯一索引）。 */
    private final long userId;

    /** 签到日期（{@code YYYY-MM-DD}，复合唯一索引）。 */
    private final CheckinDate checkinDate;

    /** 本次发放的奖励额度（DB-SCHEMA §12 quota_awarded not null，落在配置区间内）。 */
    private final long quotaAwarded;

    /** 创建时间 epoch 秒（DB-SCHEMA §12 created_at bigint）。 */
    private final long createdAt;

    private Checkin(Long id, long userId, CheckinDate checkinDate, long quotaAwarded, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.checkinDate = checkinDate;
        this.quotaAwarded = quotaAwarded;
        this.createdAt = createdAt;
    }

    /**
     * 新建一条签到记录（聚合根工厂方法，PRD GR-1 §4「写 Checkin 记录」）。
     *
     * <p>在此守护内存不变量：用户 id 必为正、日期非空、奖励额度非负。奖励额度由调用方先用
     * {@code CheckinSetting.drawReward()} 抽取后传入（额度产生规则属配置值对象语义，不在本聚合内
     * 重复随机逻辑，保持单一职责）。</p>
     *
     * @param userId       归属用户 id（&gt; 0）
     * @param checkinDate  签到日期值对象（非空，通常为「今日」）
     * @param quotaAwarded 本次奖励额度（&gt;= 0，来自配置区间随机）
     * @param createdAt    创建时间 epoch 秒
     * @return 待持久化的签到记录聚合（id 由仓储保存后回填）
     * @throws IllegalArgumentException userId 非正 / 奖励额度为负（防御式，不静默落脏数据）
     */
    public static Checkin create(long userId, CheckinDate checkinDate, long quotaAwarded, long createdAt) {
        if (userId <= 0) {
            throw new IllegalArgumentException("checkin userId must be positive, got " + userId);
        }
        Objects.requireNonNull(checkinDate, "checkinDate");
        if (quotaAwarded < 0) {
            // 奖励额度由合法配置区间抽取，理论非负；防御式拦截避免给用户记一笔负奖励。
            throw new IllegalArgumentException("checkin quotaAwarded must be non-negative, got " + quotaAwarded);
        }
        return new Checkin(null, userId, checkinDate, quotaAwarded, createdAt);
    }

    /**
     * 从持久化重建签到记录聚合（基础设施层映射用）。
     *
     * @param id           主键
     * @param userId       归属用户 id
     * @param checkinDate  签到日期值对象
     * @param quotaAwarded 奖励额度
     * @param createdAt    创建时间 epoch 秒
     * @return 重建的签到记录聚合
     */
    public static Checkin rehydrate(Long id, long userId, CheckinDate checkinDate,
                                    long quotaAwarded, long createdAt) {
        return new Checkin(id, userId, checkinDate, quotaAwarded, createdAt);
    }

    /**
     * 回填仓储生成的自增主键（持久化后调用）。
     *
     * @param assignedId 数据库生成的主键
     */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 归属用户 id */
    public long userId() {
        return userId;
    }

    /** @return 签到日期值对象 */
    public CheckinDate checkinDate() {
        return checkinDate;
    }

    /** @return 本次奖励额度 */
    public long quotaAwarded() {
        return quotaAwarded;
    }

    /** @return 创建时间 epoch 秒 */
    public long createdAt() {
        return createdAt;
    }
}
