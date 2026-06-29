package com.nexa.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 账变流水 JPA 持久化实体（基础设施层，表 {@code balance_transactions}）。
 *
 * <p>与领域模型 {@link com.nexa.billing.domain.model.BalanceTransaction} 分离，映射在
 * {@code BalanceTransactionRepositoryImpl}。后台资金审计流水，无客户读路径。</p>
 */
@Entity(name = "BillingBalanceTransactionJpaEntity")
@Table(name = "balance_transactions", indexes = {
        @Index(name = "idx_balance_tx_user_id", columnList = "user_id"),
        @Index(name = "idx_balance_tx_created", columnList = "created_time")
})
public class BalanceTransactionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** 账变类型字面量（ADMIN_CREDIT/ADMIN_DEBIT/REDEEM/TOPUP）。 */
    @Column(name = "type", columnDefinition = "varchar(32)")
    private String type;

    /** 变动额（带正负，quota 单位）。 */
    @Column(name = "amount", columnDefinition = "bigint default 0")
    private Long amount;

    /** 变动后余额快照（quota）。 */
    @Column(name = "balance_after", columnDefinition = "bigint default 0")
    private Long balanceAfter;

    /** 执行管理员 id（自助/兑换到账可空）。 */
    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "remark", columnDefinition = "varchar(512)")
    private String remark;

    @Column(name = "created_time", columnDefinition = "bigint")
    private Long createdTime;

    /** JPA 规范要求的无参构造器。 */
    public BalanceTransactionJpaEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Long balanceAfter) { this.balanceAfter = balanceAfter; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedTime() { return createdTime; }
    public void setCreatedTime(Long createdTime) { this.createdTime = createdTime; }
}
