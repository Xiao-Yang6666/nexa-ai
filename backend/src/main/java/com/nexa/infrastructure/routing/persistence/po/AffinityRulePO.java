package com.nexa.infrastructure.routing.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 亲和规则持久化实体（基础设施层，对齐 V9 {@code affinity_rules} 与 prd-channel CH-4）。
 *
 * <p>持久化映射，与领域聚合 {@link com.nexa.domain.routing.model.AffinityRule} 分离（DDD：domain 不感知持久化）。
 * 聚合↔PO 的字段/JSON 互转较复杂（key_sources/pass_headers 的 VO 列表与 Map 编解码依赖 ObjectMapper），
 * 仍由 {@code AffinityRuleRepositoryImpl} 承载，本 PO 不内置就近工厂方法（与无外部依赖的纯映射 PO 不同）。</p>
 *
 * <p>{@code key_sources}/{@code pass_headers} 用 Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)} 以 String
 * 承载 JSONB；仓储层与领域 VO/Map 互转（Jackson）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName(value = "affinity_rules", autoResultMap = true)
public class AffinityRulePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("enabled")
    private boolean enabled;

    @TableField("model_regex")
    private String modelRegex;

    @TableField("path_regex")
    private String pathRegex;

    @TableField(value = "key_sources", typeHandler = com.nexa.infrastructure.persistence.JsonbStringTypeHandler.class)
    private String keySources;

    @TableField(value = "pass_headers", typeHandler = com.nexa.infrastructure.persistence.JsonbStringTypeHandler.class)
    private String passHeaders;

    @TableField("skip_retry_on_failure")
    private boolean skipRetryOnFailure;

    @TableField("ttl_seconds")
    private long ttlSeconds;

    @TableField("built_in")
    private boolean builtIn;

    @TableField("created_time")
    private Long createdTime;

    @TableField("updated_time")
    private Long updatedTime;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public AffinityRulePO() {
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModelRegex() {
        return modelRegex;
    }

    public void setModelRegex(String modelRegex) {
        this.modelRegex = modelRegex;
    }

    public String getPathRegex() {
        return pathRegex;
    }

    public void setPathRegex(String pathRegex) {
        this.pathRegex = pathRegex;
    }

    public String getKeySources() {
        return keySources;
    }

    public void setKeySources(String keySources) {
        this.keySources = keySources;
    }

    public String getPassHeaders() {
        return passHeaders;
    }

    public void setPassHeaders(String passHeaders) {
        this.passHeaders = passHeaders;
    }

    public boolean isSkipRetryOnFailure() {
        return skipRetryOnFailure;
    }

    public void setSkipRetryOnFailure(boolean skipRetryOnFailure) {
        this.skipRetryOnFailure = skipRetryOnFailure;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
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
}
