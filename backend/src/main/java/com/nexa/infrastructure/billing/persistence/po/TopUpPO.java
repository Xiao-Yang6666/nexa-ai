package com.nexa.infrastructure.billing.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.billing.model.TopUp;
import com.nexa.domain.billing.vo.Money;
import com.nexa.domain.billing.vo.PaymentStatus;
import com.nexa.domain.billing.vo.Quota;

import java.math.BigDecimal;

/**
 * 充值订单持久化实体（基础设施层，表 {@code top_ups}）。
 *
 * <p>对齐 DATA-MODEL §7 TopUp。与领域聚合 {@link TopUp} 分离（DDD：domain 不感知 PO），映射由本类的
 * 就近工厂方法 {@link #toDomain()} / {@link #of(TopUp)} 承载。{@code trade_no} 为商户订单号
 * （幂等键），唯一索引保证回调幂等。</p>
 *
 * <p>可见性：本表为后台资金流水，无客户读路径。{@code money}（真实货币支付金额）为内部财务字段，
 * 不出现在面向客户的 DTO（仅 admin/root 管理端可见）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("top_ups")
public class TopUpPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Integer userId;

    /** 充值额度（内部配额单位，入账目标）。 */
    @TableField("amount")
    private Long amount;

    /** 支付金额（真实货币，numeric/decimal 精度安全，标度 6）。 */
    @TableField("money")
    private BigDecimal money;

    /** 商户订单号（幂等键，唯一索引）。 */
    @TableField("trade_no")
    private String tradeNo;

    @TableField("payment_method")
    private String paymentMethod;

    @TableField("payment_provider")
    private String paymentProvider;

    /** 状态字符串编码（pending/success）。 */
    @TableField("status")
    private String status;

    @TableField("create_time")
    private Long createTime;

    @TableField("complete_time")
    private Long completeTime;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public TopUpPO() {
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域聚合（重建方向）：走 {@link TopUp#builder()} 具名链式装配。值对象构造期的空值兜底
     * （{@code amount} null→0L 经 {@link Quota#of}、{@code money} null→{@link BigDecimal#ZERO}
     * 经 {@link Money#of}）留在此处；{@code status} 经 {@link PaymentStatus#fromCode} 解析，null
     * 兜底为 {@link PaymentStatus#PENDING}。
     *
     * @return 重建的充值订单聚合
     */
    public TopUp toDomain() {
        return TopUp.builder()
                .id(id)
                .userId(userId)
                .amount(Quota.of(amount == null ? 0L : amount))
                .money(Money.of(money == null ? BigDecimal.ZERO : money))
                .tradeNo(tradeNo)
                .paymentMethod(paymentMethod)
                .paymentProvider(paymentProvider)
                .createTime(createTime)
                .completeTime(completeTime)
                .status(PaymentStatus.fromCode(status == null ? PaymentStatus.PENDING.code() : status))
                .build();
    }

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，值对象取标量值，{@code status} 写入状态 code，
     * null 兜底为 {@link PaymentStatus#PENDING} code，无副作用于入参。
     *
     * @param t 充值订单聚合（非空）
     * @return 待持久化的 PO
     */
    public static TopUpPO of(TopUp t) {
        TopUpPO po = new TopUpPO();
        po.id = t.id();
        po.userId = t.userId();
        po.amount = t.amount() == null ? null : t.amount().value();
        po.money = t.money() == null ? null : t.money().value();
        po.tradeNo = t.tradeNo();
        po.paymentMethod = t.paymentMethod();
        po.paymentProvider = t.paymentProvider();
        po.status = t.status() == null ? PaymentStatus.PENDING.code() : t.status().code();
        po.createTime = t.createTime();
        po.completeTime = t.completeTime();
        return po;
    }
}
