package com.nexa.account.provider.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * 账号-分组关联 JPA 实体（基础设施层，V29 {@code account_groups} 表，复合主键 account_id+group_id）。
 *
 * <p>承载「账号归属分组及组内优先级」。由 {@code AccountRepositoryImpl} 在账号 save 时 fan-out、
 * delete 时 fan-in 维护（仿 channel→abilities），不建独立领域聚合（是 Account 聚合的派生关联）。</p>
 */
@Entity
@Table(name = "account_groups", indexes = {
        @Index(name = "idx_account_groups_group", columnList = "group_id")
})
@IdClass(AccountGroupJpaEntity.AccountGroupPK.class)
public class AccountGroupJpaEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "priority", nullable = false)
    private int priority = 50;

    /** JPA 无参构造器。 */
    public AccountGroupJpaEntity() {
    }

    public AccountGroupJpaEntity(Long accountId, Long groupId, int priority) {
        this.accountId = accountId;
        this.groupId = groupId;
        this.priority = priority;
    }

    // ---- 访问器 ----

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** 复合主键类（JPA IdClass 要求）。 */
    public static class AccountGroupPK implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long accountId;
        private Long groupId;

        public AccountGroupPK() {
        }

        public AccountGroupPK(Long accountId, Long groupId) {
            this.accountId = accountId;
            this.groupId = groupId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AccountGroupPK that)) return false;
            return Objects.equals(accountId, that.accountId)
                    && Objects.equals(groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, groupId);
        }

        public Long getAccountId() { return accountId; }
        public Long getGroupId() { return groupId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
    }
}
