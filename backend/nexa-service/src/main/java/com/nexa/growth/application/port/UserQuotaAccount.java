package com.nexa.growth.application.port;

/**
 * 用户可用额度账户端口（应用层定义，基础设施层实现，跨 bounded context 防腐端口）。
 *
 * <p>增长域需要给用户可用余额入账（{@code users.quota += amount}）：签到发放随机额度（GR-1）、
 * 邀请额度划转为可用额度（GR-5）。但 {@code users} 表归账号域（com.nexa.account）所有。为不反向依赖
 * 账号域聚合/JPA 实体造成 context 耦合，在此定义最小能力端口，由 infra 适配器用 JDBC 直接对
 * {@code users.quota} 列做原子自增实现（与计费域 {@code com.nexa.billing} 的 UserQuotaAccount 同策略，
 * 各 context 各持自己的端口，互不依赖）。</p>
 *
 * <p>backend-engineer §2.3 依赖倒置：application 只依赖本接口，可在单测中桩替换，无需起 DB。</p>
 */
public interface UserQuotaAccount {

    /**
     * 给用户可用余额原子入账（GR-1 签到发额度 / GR-5 划转加可用额度）。
     *
     * <p>语义：{@code users.quota += amount}（SQL 级原子自增，杜绝并发丢更新）。入账与签到记录写入 /
     * 邀请额度扣减由调用方在<b>同一事务</b>内完成，保证原子提交（PRD GR-1 §5「记录写入与额度增加
     * 原子」、GR-5 §3「aff_quota-=quota、quota+=quota 原子」）。</p>
     *
     * @param userId 目标用户 id（&gt; 0）
     * @param amount 入账额度（&gt;= 0）
     * @throws com.nexa.growth.domain.exception.GrowthUserNotFoundException 用户不存在/已软删除时
     */
    void credit(long userId, long amount);
}
