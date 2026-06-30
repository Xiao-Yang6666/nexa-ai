package com.nexa.infrastructure.account.provider.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Objects;

/**
 * 账号-分组关联持久化实体（基础设施层，V31 {@code account_groups} 表，复合主键 account_id+group 字符串）。
 *
 * <p>承载「账号归属分组及组内优先级」。由 {@code AccountRepositoryImpl} 在账号 save 时 fan-out、
 * delete 时 fan-in 维护（仿 channel→abilities），不建独立领域聚合（是 Account 聚合的派生关联）。</p>
 *
 * <p>group 为字符串分组（对齐 channel/user/abilities 的 group 约定，account 与 channel 在同一字符串
 * group 下汇合选渠）。{@code group} 是 SQL 保留字，列名加双引号转义。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：复合主键无自增列，MyBatis-Plus 侧以 {@code account_id} 标
 * {@code @TableId(INPUT)}（仅满足框架对单一主键字段的要求），{@code group} 作普通 {@code @TableField}。
 * 仓储只用 wrapper 按 {@code account_id}/{@code group} 增删查（从不按复合主键 selectById），故此标注足够。</p>
 */
@TableName("account_groups")
public class AccountGroupPO {

    @TableId(value = "account_id", type = IdType.INPUT)
    private Long accountId;

    @TableField("\"group\"")
    private String group;

    @TableField("priority")
    private int priority = 50;

    /** JPA 无参构造器。 */
    public AccountGroupPO() {
    }

    public AccountGroupPO(Long accountId, String group, int priority) {
        this.accountId = accountId;
        this.group = group;
        this.priority = priority;
    }

    // ---- 访问器 ----

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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** 复合主键类（JPA IdClass 要求）。 */
    public static class AccountGroupPK implements Serializable {
        private static final long serialVersionUID = 2L;
        private Long accountId;
        private String group;

        public AccountGroupPK() {
        }

        public AccountGroupPK(Long accountId, String group) {
            this.accountId = accountId;
            this.group = group;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AccountGroupPK that)) return false;
            return Objects.equals(accountId, that.accountId)
                    && Objects.equals(group, that.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, group);
        }

        public Long getAccountId() { return accountId; }
        public String getGroup() { return group; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public void setGroup(String group) { this.group = group; }
    }
}
