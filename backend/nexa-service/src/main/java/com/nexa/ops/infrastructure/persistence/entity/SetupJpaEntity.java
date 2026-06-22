package com.nexa.ops.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 系统初始化标记 JPA 持久化实体（基础设施层，对齐 V18 {@code setups}）。
 *
 * <p>与领域聚合 {@link com.nexa.ops.domain.setup.SetupMarker} 分离（DDD：domain 不感知 JPA）。
 * 单行哨兵，主键 {@code id} 由领域固定为 {@code SetupMarker.SINGLETON_ID}（非自增），并发双提交由
 * DB 主键唯一兜底。映射转换在 {@code SetupRepositoryImpl}。</p>
 */
@Entity
@Table(name = "setups")
public class SetupJpaEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "initialized_at", nullable = false)
    private Long initializedAt;

    /** JPA 无参构造（框架反射用）。 */
    protected SetupJpaEntity() {
    }

    /**
     * @param id            主键（单行哨兵恒为 1）
     * @param version       引导版本
     * @param initializedAt 初始化时间 epoch 秒
     */
    public SetupJpaEntity(Integer id, String version, Long initializedAt) {
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
}
