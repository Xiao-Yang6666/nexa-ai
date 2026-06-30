package com.nexa.infrastructure.model.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.model.model.ModelMeta;

/**
 * 模型元数据持久化实体（基础设施层，对齐 V10 {@code model_metas} 与 DB-SCHEMA 模块四 §4.1）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.model.model.ModelMeta} 分离（DDD：domain 不感知持久化框架）。
 * 映射由本类就近工厂方法 {@link #toDomain()} / {@link #of(ModelMeta)} 承载。软删除用 {@code deleted_at} +
 * {@code @SQLRestriction}，查询自动过滤已删行。description/icon/tags/endpoints/name_rule 用 text 承载（可长）。</p>
 *
 * <p>软删除：MyBatis-Plus 侧以 {@code @TableLogic(value = "null")} 让 {@code select} 自动追加
 * {@code deleted_at IS NULL} 过滤（等价 JPA {@code @SQLRestriction}）；软删除<b>写</b>操作改由 Mapper 显式
 * {@code @Update} 打 epoch 秒时间戳，不用 {@code deleteById}。并存期保留 {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("model_metas")
public class ModelMetaPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_name")
    private String modelName;

    @TableField("status")
    private int status;

    @TableField("description")
    private String description;

    @TableField("icon")
    private String icon;

    @TableField("tags")
    private String tags;

    @TableField("vendor_id")
    private Long vendorId;

    @TableField("endpoints")
    private String endpoints;

    @TableField("name_rule")
    private String nameRule;

    @TableField("sync_official")
    private int syncOfficial;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public ModelMetaPO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }

    public String getNameRule() {
        return nameRule;
    }

    public void setNameRule(String nameRule) {
        this.nameRule = nameRule;
    }

    public int getSyncOfficial() {
        return syncOfficial;
    }

    public void setSyncOfficial(int syncOfficial) {
        this.syncOfficial = syncOfficial;
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，无副作用于入参。
     *
     * @param m 模型元数据领域对象（非空）
     * @return 待持久化的 PO
     */
    public static ModelMetaPO of(ModelMeta m) {
        ModelMetaPO e = new ModelMetaPO();
        e.id = m.id();
        e.modelName = m.modelName();
        e.status = m.status().code();
        e.description = m.description();
        e.icon = m.icon();
        e.tags = m.tags();
        e.vendorId = m.vendorId();
        e.endpoints = m.endpoints();
        e.nameRule = m.nameRule();
        e.syncOfficial = m.syncOfficial();
        e.createdTime = m.createdTime();
        e.updatedTime = m.updatedTime();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link ModelMeta#builder()}）。
     *
     * @return 重建的模型元数据领域对象
     */
    public ModelMeta toDomain() {
        return ModelMeta.builder()
                .id(id)
                .modelName(modelName)
                .status(status)
                .description(description)
                .icon(icon)
                .tags(tags)
                .vendorId(vendorId)
                .endpoints(endpoints)
                .nameRule(nameRule)
                .syncOfficial(syncOfficial)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }
}
