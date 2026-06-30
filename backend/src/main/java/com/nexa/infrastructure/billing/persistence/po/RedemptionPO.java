package com.nexa.infrastructure.billing.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.billing.model.Redemption;
import com.nexa.domain.billing.vo.Quota;
import com.nexa.domain.billing.vo.RedemptionStatus;

/**
 * 兑换码持久化实体（基础设施层）。
 *
 * <p>对齐 DB-SCHEMA §6 Redemption / 表 {@code redemptions}。与领域聚合
 * {@link Redemption} 分离（DDD：domain 不感知持久化框架），映射由本类就近工厂方法
 * {@link #toDomain()} / {@link #of(Redemption)} 承载。{@code key} 为 PG 保留字，列名双引号转义。</p>
 *
 * <p>软删除：{@code deleted_at} 为可空 epoch 秒时间戳。MyBatis-Plus 侧以 {@code @TableLogic(value = "null")}
 * 让 {@code select} 自动追加 {@code deleted_at IS NULL} 过滤（等价 JPA {@code @SQLRestriction}）。
 * 本聚合仓储无软删除写路径（仅 findByKey/save/saveAll/findPage），故不需 Mapper 显式打时间戳 {@code @Update}。
 * 并存期保留 {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("redemptions")
public class RedemptionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Integer userId;

    /** 兑换码明文（char(32)，PG 保留字 key 需双引号转义；唯一索引）。 */
    @TableField("\"key\"")
    private String key;

    @TableField("status")
    private Integer status;

    @TableField("name")
    private String name;

    @TableField("quota")
    private Integer quota;

    @TableField("created_time")
    private Long createdTime;

    @TableField("redeemed_time")
    private Long redeemedTime;

    @TableField("used_user_id")
    private Integer usedUserId;

    @TableField("expired_time")
    private Long expiredTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public RedemptionPO() {
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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getQuota() {
        return quota;
    }

    public void setQuota(Integer quota) {
        this.quota = quota;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }

    public Long getRedeemedTime() {
        return redeemedTime;
    }

    public void setRedeemedTime(Long redeemedTime) {
        this.redeemedTime = redeemedTime;
    }

    public Integer getUsedUserId() {
        return usedUserId;
    }

    public void setUsedUserId(Integer usedUserId) {
        this.usedUserId = usedUserId;
    }

    public Long getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(Long expiredTime) {
        this.expiredTime = expiredTime;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）。
     *
     * @param r 兑换码聚合
     * @return 待持久化的 PO
     */
    public static RedemptionPO of(Redemption r) {
        RedemptionPO e = new RedemptionPO();
        e.id = r.id();
        e.userId = r.creatorUserId();
        e.key = r.key();
        e.status = r.status().code();
        e.name = r.name();
        // quota 列为 integer（DB-SCHEMA §6），面额按现网整数语义；面额超 int 范围属配置错误，此处转 int。
        e.quota = (int) r.quota().value();
        e.createdTime = r.createdTime();
        e.redeemedTime = r.redeemedTime();
        e.usedUserId = r.usedUserId();
        e.expiredTime = r.expiredTime();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link Redemption#builder()}）。
     *
     * @return 重建的兑换码聚合
     */
    public Redemption toDomain() {
        // 过期时间 null→0（永不过期）的兜底已收敛进 Redemption.Builder.expiredTime，此处不再三元。
        return Redemption.builder()
                .id(id)
                .creatorUserId(userId)
                .key(key == null ? null : key.trim())  // char(32) 定长右补空格，去尾
                .status(RedemptionStatus.fromCode(status == null ? RedemptionStatus.UNUSED.code() : status))
                .name(name)
                .quota(Quota.of(quota == null ? 0L : quota))
                .createdTime(createdTime)
                .redeemedTime(redeemedTime)
                .usedUserId(usedUserId)
                .expiredTime(expiredTime)
                .build();
    }
}
