package com.nexa.infrastructure.model.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.model.model.PublicModel;

import java.math.BigDecimal;

/**
 * 对外模型商品目录 JPA 持久化实体（基础设施层，对齐 V11 {@code public_models} 与 DB-SCHEMA §16）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.model.model.PublicModel} 分离，映射由就近工厂方法
 * {@link #toDomain()} / {@link #of(PublicModel)} 承载。</p>
 *
 * <p>软删除：{@code deleted_at} 为可空 epoch 秒。MyBatis-Plus 侧以 {@code @TableLogic(value = "null")}
 * 让 {@code select} 自动追加 {@code deleted_at IS NULL} 过滤（等价 JPA {@code @SQLRestriction}）；软删<b>写</b>
 * 不依赖 {@code deleteById}（删除值是 epoch 秒而非 0/1），改由 Mapper 显式 {@code @Update} 打时间戳。并存期保留
 * {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("public_models")
public class PublicModelPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("public_name")
    private String publicName;

    @TableField("base_price_ratio")
    private BigDecimal basePriceRatio;

    @TableField("use_price")
    private Boolean usePrice;

    @TableField("base_price")
    private BigDecimal basePrice;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("display_name")
    private String displayName;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("description")
    private String description;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public PublicModelPO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicName() { return publicName; }
    public void setPublicName(String publicName) { this.publicName = publicName; }
    public BigDecimal getBasePriceRatio() { return basePriceRatio; }
    public void setBasePriceRatio(BigDecimal basePriceRatio) { this.basePriceRatio = basePriceRatio; }
    public Boolean getUsePrice() { return usePrice; }
    public void setUsePrice(Boolean usePrice) { this.usePrice = usePrice; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Long updatedTime) { this.updatedTime = updatedTime; }
    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，无副作用于入参。
     *
     * @param m 对外模型领域聚合（非空）
     * @return 待持久化的 PO
     */
    public static PublicModelPO of(PublicModel m) {
        PublicModelPO e = new PublicModelPO();
        e.id = m.id();
        e.publicName = m.publicName();
        e.basePriceRatio = m.basePriceRatio();
        e.usePrice = m.usePrice();
        e.basePrice = m.basePrice();
        e.enabled = m.enabled();
        e.displayName = m.displayName();
        e.sortOrder = m.sortOrder();
        e.description = m.description();
        e.createdTime = m.createdTime();
        e.updatedTime = m.updatedTime();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link PublicModel#builder()}）。
     *
     * @return 重建的对外模型聚合
     */
    public PublicModel toDomain() {
        return PublicModel.builder()
                .id(id)
                .publicName(publicName)
                .basePriceRatio(basePriceRatio)
                .usePrice(usePrice)
                .basePrice(basePrice)
                .enabled(enabled)
                .displayName(displayName)
                .sortOrder(sortOrder)
                .description(description)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }
}
