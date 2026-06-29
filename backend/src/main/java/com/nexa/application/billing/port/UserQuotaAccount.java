package com.nexa.application.billing.port;

import com.nexa.domain.billing.vo.Quota;

/**
 * 用户额度账户端口（应用层定义，基础设施层实现）。
 *
 * <p>跨 bounded context 协作的<b>防腐端口</b>：计费域（com.nexa.billing）需要给用户余额入账
 * （充值/兑换码 {@code user.Quota += amount}，prd-billing BL-1/BL-4），但用户聚合属于账号域
 * （com.nexa.account）。为避免 billing 直接依赖 account 的仓储/聚合造成 context 耦合，
 * 在此定义最小能力端口，由 infra 适配器实现（直接操作 {@code users.quota} 列）。</p>
 *
 * <p>backend-engineer §2.3 依赖倒置 + DDD bounded context 解耦：application 只依赖本接口，
 * 可在单测中桩替换，无需起 DB 或拉入账号域。</p>
 */
public interface UserQuotaAccount {

    /**
     * 给用户余额入账（充值/兑换码加额度，prd-billing BL-1 pay_credit / BL-4 rd_credit）。
     *
     * <p>语义：{@code users.quota += amount}（原子自增，杜绝并发读改写丢更新）。入账与业务单据
     * 的状态变更（订单置 success / 兑换码置已用）由调用方在同一事务内完成，保证原子性。</p>
     *
     * @param userId 目标用户 id（&gt; 0）
     * @param amount 入账额度（quota 单位，&gt;= 0）
     * @throws com.nexa.domain.billing.exception.InvalidBillingParameterException 用户不存在时
     */
    void credit(long userId, Quota amount);

    /**
     * 从用户余额扣减额度（relay 转发结算扣售价，prd-relay RL-7 第19步 SettleBilling）。
     *
     * <p>语义：{@code users.quota -= amount}（原子自减，杜绝并发结算丢更新）。本期为「响应后一次性
     * 结算扣减」最小闭环——无选渠预扣，直接按真实 usage 算得的 {@code quota_sell} 扣减一次。完整的
     * 「选渠后预扣 + 响应后多退少补」分段结算（BILLING-MODEL-ARCHITECTURE §6 第8-9/19步）待后续接入。</p>
     *
     * @param userId 目标用户 id（&gt; 0）
     * @param amount 扣减额度（quota 单位，&gt;= 0；0 为无副作用空操作）
     * @throws com.nexa.domain.billing.exception.InvalidBillingParameterException 用户不存在时
     */
    void debit(long userId, Quota amount);

    /**
     * 从用户余额扣减，<b>扣到 0 为止</b>（管理员手动扣费，不允许欠费）。
     *
     * <p>原子语义：{@code users.quota = GREATEST(quota - amount, 0)}，并返回<b>实际扣减额</b>
     * （= 扣前余额与请求额的较小者）。请求额超出当前余额时只扣到 0；实扣额用于记账变流水。
     * 单条 SQL 原子完成（先读后扣放在同一事务/语句），杜绝并发竞态。</p>
     *
     * @param userId 目标用户 id（&gt; 0）
     * @param amount 期望扣减额（quota，&gt;= 0）
     * @return 实际扣减额（quota，&lt;= amount）
     * @throws com.nexa.domain.billing.exception.InvalidBillingParameterException 用户不存在时
     */
    Quota debitToZero(long userId, Quota amount);

    /**
     * 查询用户当前余额（用于回执展示，可选）。
     *
     * @param userId 目标用户 id
     * @return 当前余额额度（用户不存在时为 {@link Quota#ZERO}）
     */
    Quota balanceOf(long userId);
}
