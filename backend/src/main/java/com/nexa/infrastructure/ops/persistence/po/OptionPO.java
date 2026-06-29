package com.nexa.infrastructure.ops.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 全站选项 JPA 持久化实体（基础设施层，对齐 V17 {@code options} 与 DB-SCHEMA §18）。
 *
 * <p>持久化映射，与领域值对象 {@link com.nexa.domain.ops.option.Option} 分离（DDD：domain 不感知 JPA）。
 * 映射转换在 {@code OptionRepositoryImpl}。{@code key} 为 PG 保留字，列名加双引号转义（与 V17 一致）。
 * 全局 KV 无软删除，不带 {@code @SQLRestriction}。</p>
 */
@Entity
@Table(name = "options")
public class OptionPO {

    @Id
    @Column(name = "\"key\"", length = 255)
    private String key;

    @Column(name = "value", columnDefinition = "text")
    private String value;

    /** JPA 无参构造（框架反射用）。 */
    protected OptionPO() {
    }

    /**
     * @param key   配置键
     * @param value 配置值（可空）
     */
    public OptionPO(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /** @return 配置键 */
    public String getKey() {
        return key;
    }

    /** @param key 配置键 */
    public void setKey(String key) {
        this.key = key;
    }

    /** @return 配置值 */
    public String getValue() {
        return value;
    }

    /** @param value 配置值 */
    public void setValue(String value) {
        this.value = value;
    }
}
