package com.nexa.infrastructure.growth.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.growth.model.Checkin;
import com.nexa.domain.growth.vo.CheckinDate;

/**
 * 签到记录持久化实体（基础设施层，对齐 V17 {@code checkins} 与 DB-SCHEMA §12）。
 *
 * <p>持久化映射，与领域聚合 {@link Checkin} 分离（DDD：domain 不感知持久化框架）。映射由本类就近工厂方法
 * {@link #toDomain()} / {@link #of(Checkin)} 承载。复合唯一约束 {@code idx_user_checkin_date (user_id,
 * checkin_date)} 守护「同一用户同日仅一条」不变量（PRD GR-1 §4 并发重复拦截）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("checkins")
public class CheckinPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Integer userId;

    @TableField("checkin_date")
    private String checkinDate;

    @TableField("quota_awarded")
    private Long quotaAwarded;

    @TableField("created_at")
    private Long createdAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public CheckinPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate(String checkinDate) {
        this.checkinDate = checkinDate;
    }

    public Long getQuotaAwarded() {
        return quotaAwarded;
    }

    public void setQuotaAwarded(Long quotaAwarded) {
        this.quotaAwarded = quotaAwarded;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）。
     *
     * @param c 签到记录聚合
     * @return 待持久化的 PO
     */
    public static CheckinPO of(Checkin c) {
        CheckinPO e = new CheckinPO();
        e.id = c.id();
        e.userId = (int) c.userId();
        e.checkinDate = c.checkinDate().toWire();
        e.quotaAwarded = c.quotaAwarded();
        e.createdAt = c.createdAt();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link Checkin#rehydrate}）。
     *
     * @return 重建的签到记录聚合
     */
    public Checkin toDomain() {
        return Checkin.rehydrate(
                id,
                userId == null ? 0L : userId,
                CheckinDate.parse(checkinDate),
                quotaAwarded == null ? 0L : quotaAwarded,
                createdAt == null ? 0L : createdAt);
    }
}
