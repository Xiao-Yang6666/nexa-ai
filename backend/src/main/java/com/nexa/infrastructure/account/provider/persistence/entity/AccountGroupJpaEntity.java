package com.nexa.infrastructure.account.provider.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * 账号-分组关联 JPA 实体（基础设施层，V31 {@code account_groups} 表，复合主键 account_id+group 字符串）。
 *
 * <p>承载「账号归属分组及组内优先级」。由 {@code AccountRepositoryImpl} 在账号 save 时 fan-out、
 * delete 时 fan-in 维护（仿 channel→abilities），不建独立领域聚合（是 Account 聚合的派生关联）。</p>
 *
 * <p>group 为字符串分组（对齐 channel/user/abilities 的 group 约定，account 与 channel 在同一字符串
 * group 下汇合选渠）。{@code group} 是 SQL 保留字，列名加双引号转义。</p>
 */
@Entity
@Table(name = "account_groups", indexes = {
        @Index(name = "idx_account_groups_group", columnList = "\"group\"")
})
@IdClass(AccountGroupJpaEntity.AccountGroupPK.class)
public class AccountGroupJpaEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Id
    @Column(name = "\"group\"", nullable = false, length = 64)
    private String group;

    @Column(name = "priority", nullable = false)
    private int priority = 50;

    /** JPA 无参构造器。 */
    public AccountGroupJpaEntity() {
    }

    public AccountGroupJpaEntity(Long accountId, String group, int priority) {
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
