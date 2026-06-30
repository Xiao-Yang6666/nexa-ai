package com.nexa.infrastructure.modelgroup.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.infrastructure.persistence.JsonbStringTypeHandler;

import java.math.BigDecimal;

/**
 * 模型组持久化实体（基础设施层，对齐 V26 {@code model_groups}）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.modelgroup.model.ModelGroup} 分离（DDD：domain
 * 不感知持久化框架）。映射转换在 {@code ModelGroupRepositoryImpl}（{@code models} JSON ⇄ {@code ModelNames}
 * 用 {@code ObjectMapper}，异常翻译为 {@code ModelGroupPersistenceException}，保留在 Impl）。</p>
 *
 * <p>{@code models} 是 PG {@code jsonb} 列，以 String 承载——MyBatis-Plus 侧由 {@link JsonbStringTypeHandler}
 * 完成 String↔jsonb 互转（{@code autoResultMap = true} 使读取亦走该 Handler），并存期保留 JPA 的
 * {@code @JdbcTypeCode(SqlTypes.JSON)}。软删除：{@code deleted_at} 可空 epoch 秒，MyBatis-Plus 侧以
 * {@code @TableLogic(value = "null")} 让 select 自动追加 {@code deleted_at IS NULL}（等价 JPA
 * {@code @SQLRestriction}），软删除<b>写</b>由 Mapper 显式 {@code @Update} 打时间戳（不用 {@code deleteById}）。
 * {@code code} 唯一索引 {@code uk_model_group_code}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName(value = "model_groups", autoResultMap = true)
public class ModelGroupPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("code")
    private String code;

    @TableField("base_price_ratio")
    private BigDecimal basePriceRatio;

    /** 可用模型 JSON 字符串数组（如 {@code ["gpt-4o","claude-3-opus"]}），JSONB 承载。 */
    @TableField(value = "models", typeHandler = JsonbStringTypeHandler.class)
    private String models;

    @TableField("access_policy")
    private String accessPolicy;

    @TableField("status")
    private int status;

    @TableField("description")
    private String description;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** 框架（MyBatis-Plus）实例化所需的无参构造器。 */
    public ModelGroupPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getBasePriceRatio() {
        return basePriceRatio;
    }

    public void setBasePriceRatio(BigDecimal basePriceRatio) {
        this.basePriceRatio = basePriceRatio;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getAccessPolicy() {
        return accessPolicy;
    }

    public void setAccessPolicy(String accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Long createdTime) {
        this.createdTime = createdTime;
    }

    public Long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }
}
