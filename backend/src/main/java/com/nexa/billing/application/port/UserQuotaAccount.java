package com.nexa.billing.application.port;

import com.nexa.billing.domain.vo.Quota;

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
     * @throws com.nexa.billing.domain.exception.InvalidBillingParameterException 用户不存在时
     */
    void credit(long userId, Quota amount);

    /**
     * 查询用户当前余额（用于回执展示，可选）。
     *
     * @param userId 目标用户 id
     * @return 当前余额额度（用户不存在时为 {@link Quota#ZERO}）
     */
    Quota balanceOf(long userId);
}
