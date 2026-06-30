package com.nexa.infrastructure.billing.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.billing.model.BalanceTransaction;
import com.nexa.domain.billing.vo.BalanceTransactionType;

/**
 * 账变流水持久化实体（基础设施层，表 {@code balance_transactions}）。
 *
 * <p>与领域模型 {@link BalanceTransaction} 分离，映射由本类的就近工厂方法
 * {@link #toDomain()} / {@link #of(BalanceTransaction)} 承载。后台资金审计流水，无客户读路径。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("balance_transactions")
public class BalanceTransactionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** 账变类型字面量（ADMIN_CREDIT/ADMIN_DEBIT/REDEEM/TOPUP）。 */
    @TableField("type")
    private String type;

    /** 变动额（带正负，quota 单位）。 */
    @TableField("amount")
    private Long amount;

    /** 变动后余额快照（quota）。 */
    @TableField("balance_after")
    private Long balanceAfter;

    /** 执行管理员 id（自助/兑换到账可空）。 */
    @TableField("operator_id")
    private Long operatorId;

    @TableField("remark")
    private String remark;

    @TableField("created_time")
    private Long createdTime;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public BalanceTransactionPO() {
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域聚合（重建方向）：走 {@link BalanceTransaction#rehydrate}，{@code type} 经
     * {@link BalanceTransactionType#fromWire} 解析，数值列 null 兜底为 {@code 0L}。
     *
     * @return 重建的账变领域对象
     */
    public BalanceTransaction toDomain() {
        return BalanceTransaction.rehydrate(
                id,
                userId == null ? 0L : userId,
                BalanceTransactionType.fromWire(type),
                amount == null ? 0L : amount,
                balanceAfter == null ? 0L : balanceAfter,
                operatorId,
                remark,
                createdTime == null ? 0L : createdTime);
    }

    /**
     * 领域聚合 → PO（持久化方向）：逐字段映射，{@code type} 写入枚举 wire 码，无副作用于入参。
     *
     * @param t 账变领域对象（非空）
     * @return 待持久化的 PO
     */
    public static BalanceTransactionPO of(BalanceTransaction t) {
        BalanceTransactionPO po = new BalanceTransactionPO();
        po.id = t.id();
        po.userId = t.userId();
        po.type = t.type().wireValue();
        po.amount = t.amount();
        po.balanceAfter = t.balanceAfter();
        po.operatorId = t.operatorId();
        po.remark = t.remark();
        po.createdTime = t.createdTime();
        return po;
    }
}
