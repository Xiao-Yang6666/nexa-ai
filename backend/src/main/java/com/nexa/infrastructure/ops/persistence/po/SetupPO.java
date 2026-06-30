package com.nexa.infrastructure.ops.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.ops.setup.SetupMarker;

/**
 * 系统初始化标记持久化实体（基础设施层，对齐 V18 {@code setups}）。
 *
 * <p>与领域聚合 {@link com.nexa.domain.ops.setup.SetupMarker} 分离（DDD：domain 不感知持久化）。
 * 单行哨兵，主键 {@code id} 由领域固定为 {@code SetupMarker.SINGLETON_ID}（非自增），并发双提交由
 * DB 主键唯一兜底。映射转换由本类就近工厂方法 {@link #toDomain()} / {@link #of(SetupMarker)} 承载。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("setups")
public class SetupPO {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    @TableField("version")
    private String version;

    @TableField("initialized_at")
    private Long initializedAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    protected SetupPO() {
    }

    /**
     * @param id            主键（单行哨兵恒为 1）
     * @param version       引导版本
     * @param initializedAt 初始化时间 epoch 秒
     */
    public SetupPO(Integer id, String version, Long initializedAt) {
        this.id = id;
        this.version = version;
        this.initializedAt = initializedAt;
    }

    /** @return 主键 */
    public Integer getId() {
        return id;
    }

    /** @param id 主键 */
    public void setId(Integer id) {
        this.id = id;
    }

    /** @return 引导版本 */
    public String getVersion() {
        return version;
    }

    /** @param version 引导版本 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** @return 初始化时间 epoch 秒 */
    public Long getInitializedAt() {
        return initializedAt;
    }

    /** @param initializedAt 初始化时间 epoch 秒 */
    public void setInitializedAt(Long initializedAt) {
        this.initializedAt = initializedAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域聚合（重建方向）：走 {@link SetupMarker#rehydrate}，{@code initializedAt} null 兜底为 {@code 0L}。
     *
     * @return 重建的初始化标记
     */
    public SetupMarker toDomain() {
        return SetupMarker.rehydrate(
                id,
                version,
                initializedAt == null ? 0L : initializedAt);
    }

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射。
     *
     * @param marker 初始化标记（非空）
     * @return 待持久化的 PO
     */
    public static SetupPO of(SetupMarker marker) {
        return new SetupPO(marker.id(), marker.version(), marker.initializedAt());
    }
}
