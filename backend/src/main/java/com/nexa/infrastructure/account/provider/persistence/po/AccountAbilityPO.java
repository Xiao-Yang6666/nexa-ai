package com.nexa.infrastructure.account.provider.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Ability 路由索引实体（账号级路由，V33 重建）。
 *
 * <p>承载 account_id × group × models 反向索引，用于快速账号选择。
 * 由 {@code AccountRepositoryImpl} 在账号保存时 fan-out 维护。{@code group} 为 PG 保留字双引号转义。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留 JPA 注解，新增 MyBatis-Plus 注解。</p>
 */
@TableName("abilities")
public class AccountAbilityPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("account_id")
    private Long accountId;

    @TableField("\"group\"")
    private String group;

    @TableField("models")
    private String models;

    @TableField("tag")
    private String tag;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    public AccountAbilityPO() {
    }

    public AccountAbilityPO(Long accountId, String group, String models, String tag,
                           String status, Long createdAt, Long updatedAt) {
        this.accountId = accountId;
        this.group = group;
        this.models = models;
        this.tag = tag;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
