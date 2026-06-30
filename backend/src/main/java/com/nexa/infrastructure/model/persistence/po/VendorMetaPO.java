package com.nexa.infrastructure.model.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.model.model.Vendor;

/**
 * 供应商元数据持久化实体（基础设施层，对齐 V10 {@code vendor_metas} 与 DB-SCHEMA 模块四 §4.2）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.model.model.Vendor} 分离（DDD：domain 不感知持久化框架）。
 * 映射由本类就近工厂方法 {@link #toDomain()} / {@link #of(Vendor)} 承载。软删除用 {@code deleted_at} 时间戳 +
 * {@code @SQLRestriction("deleted_at IS NULL")}（与 tokens 等表惯例一致），查询自动过滤已删行。</p>
 *
 * <p>软删除：MyBatis-Plus 侧以 {@code @TableLogic(value = "null")} 让 {@code select} 自动追加
 * {@code deleted_at IS NULL} 过滤（等价 JPA {@code @SQLRestriction}）；软删除<b>写</b>操作不依赖
 * {@code deleteById}（本项目删除值是 epoch 秒而非 0/1），改由 Mapper 显式 {@code @Update} 打时间戳。并存期保留
 * {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。</p>
 */
@TableName("vendor_metas")
public class VendorMetaPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("icon")
    private String icon;

    @TableField("status")
    private int status;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** JPA 规范要求的无参构造器。 */
    public VendorMetaPO() {
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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
     * @param v 供应商领域对象（非空）
     * @return 待持久化的 PO
     */
    public static VendorMetaPO of(Vendor v) {
        VendorMetaPO e = new VendorMetaPO();
        e.id = v.id();
        e.name = v.name();
        e.icon = v.icon();
        e.status = v.status().code();
        e.createdTime = v.createdTime();
        e.updatedTime = v.updatedTime();
        return e;
    }

    /**
     * PO → 领域聚合（重建方向）：走 {@link Vendor#rehydrate}。
     *
     * @return 重建的供应商领域对象
     */
    public Vendor toDomain() {
        return Vendor.rehydrate(id, name, icon, status, createdTime, updatedTime);
    }
}
