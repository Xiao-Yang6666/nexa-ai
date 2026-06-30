package com.nexa.infrastructure.ops.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.ops.option.Option;

/**
 * 全站选项持久化实体（基础设施层，对齐 V17 {@code options} 与 DB-SCHEMA §18）。
 *
 * <p>持久化映射，与领域值对象 {@link com.nexa.domain.ops.option.Option} 分离（DDD：domain 不感知持久化）。
 * 映射转换由本类就近工厂方法 {@link #toDomain()} / {@link #of(Option)} 承载。
 * {@code key} 为 PG 保留字，列名加双引号转义（与 V17 一致）。全局 KV 无软删除，不带 {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("options")
public class OptionPO {

    @TableId(value = "\"key\"", type = IdType.INPUT)
    private String key;

    @TableField("value")
    private String value;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域值对象（重建方向）：走 {@link Option#of}，键名 + 值原样回填。
     *
     * @return 重建的配置项
     */
    public Option toDomain() {
        return Option.of(key, value);
    }

    /**
     * 领域值对象 → PO（持久化方向）：键名取 {@code keyName()}，值原样映射。
     *
     * @param option 配置项（非空）
     * @return 待持久化的 PO
     */
    public static OptionPO of(Option option) {
        return new OptionPO(option.keyName(), option.value());
    }
}
