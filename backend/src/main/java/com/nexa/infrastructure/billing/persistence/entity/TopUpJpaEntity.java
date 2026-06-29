package com.nexa.infrastructure.billing.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 充值订单 JPA 持久化实体（基础设施层）。
 *
 * <p>对齐 DATA-MODEL §7 TopUp / 表 {@code top_ups}。与领域聚合
 * {@link com.nexa.domain.billing.model.TopUp} 分离（DDD：domain 不感知 JPA），映射转换在
 * {@code TopUpRepositoryImpl}。{@code trade_no} 为商户订单号（幂等键），唯一索引保证回调幂等。</p>
 *
 * <p>可见性：本表为后台资金流水，无客户读路径。{@code money}（真实货币支付金额）为内部财务字段，
 * 不出现在面向客户的 DTO（仅 admin/root 管理端可见）。</p>
 */
@Entity(name = "BillingTopUpJpaEntity")
@Table(name = "top_ups", indexes = {
        @Index(name = "idx_top_ups_trade_no", columnList = "trade_no", unique = true),
        @Index(name = "idx_top_ups_user_id", columnList = "user_id"),
        @Index(name = "idx_top_ups_status", columnList = "status")
})
public class TopUpJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    /** 充值额度（内部配额单位，入账目标）。 */
    @Column(name = "amount", columnDefinition = "bigint default 0")
    private Long amount;

    /** 支付金额（真实货币，numeric/decimal 精度安全，标度 6）。 */
    @Column(name = "money", columnDefinition = "numeric(20,6) default 0")
    private BigDecimal money;

    /** 商户订单号（幂等键，唯一索引）。 */
    @Column(name = "trade_no", columnDefinition = "varchar(255)", unique = true)
    private String tradeNo;

    @Column(name = "payment_method", columnDefinition = "varchar(64)")
    private String paymentMethod;

    @Column(name = "payment_provider", columnDefinition = "varchar(64)")
    private String paymentProvider;

    /** 状态字符串编码（pending/success）。 */
    @Column(name = "status", columnDefinition = "varchar(32) default 'pending'")
    private String status;

    @Column(name = "create_time", columnDefinition = "bigint")
    private Long createTime;

    @Column(name = "complete_time", columnDefinition = "bigint")
    private Long completeTime;

    /** JPA 规范要求的无参构造器。 */
    public TopUpJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public BigDecimal getMoney() {
        return money;
    }

    public void setMoney(BigDecimal money) {
        this.money = money;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(Long completeTime) {
        this.completeTime = completeTime;
    }
}
