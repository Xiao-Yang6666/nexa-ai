package com.nexa.growth.domain.model;

import com.nexa.growth.domain.exception.AffQuotaTransferException;

/**
 * 邀请返利账户聚合根（充血领域模型，PRD GR-4/GR-5）。
 *
 * <p>增长子域对「用户邀请返利状态」的一致性视图——它是 {@code users} 表 aff_* 列
 * （{@code aff_code/aff_count/aff_quota/aff_history/inviter_id}，DB-SCHEMA §1）在增长 bounded
 * context 内的<b>投影聚合</b>。增长域以本聚合 + 自有仓储读写这组列，而非反向依赖账号域的 User 聚合，
 * 遵循与计费域 {@code UserQuotaAccount} 相同的 bounded context 解耦策略（用户表归账号域，跨域用最小
 * 投影/端口协作，避免两个 JPA 实体映射同一表）。</p>
 *
 * <p>充血行为（backend-engineer §2.2）：返利入账（{@link #creditInviterReward}）与邀请额度划转
 * （{@link #transferToQuota}）的业务规则与不变量守护都在本聚合方法上，应用层只编排「读聚合 → 调行为 →
 * 存盘 + 同事务调额度端口」。本类零框架依赖，可纯单测。</p>
 *
 * <p>领域规则来源：prd-growth.md GR-4（返利入账）/ GR-5（额度划转）；字段对齐 DB-SCHEMA §1 User aff_*。</p>
 */
public class AffiliateAccount {

    /** 账户所属用户 id（&gt; 0）。 */
    private final long userId;

    /** 个人邀请码（4 位，可能为 null——历史/边界场景，GR-4 取码时按需生成由应用层负责）。 */
    private final String affCode;

    /** 累计邀请人数（DB-SCHEMA §1 aff_count，GR-4 归因 ++）。 */
    private long affCount;

    /** 当前可划转邀请额度（DB-SCHEMA §1 aff_quota，GR-4 += 奖励 / GR-5 划转 -=）。 */
    private long affQuota;

    /** 历史累计邀请额度（DB-SCHEMA §1 aff_history，GR-4 += 奖励，只增不减——历史口径不随划转回退）。 */
    private long affHistoryQuota;

    private AffiliateAccount(long userId, String affCode, long affCount,
                            long affQuota, long affHistoryQuota) {
        this.userId = userId;
        this.affCode = affCode;
        this.affCount = affCount;
        this.affQuota = affQuota;
        this.affHistoryQuota = affHistoryQuota;
    }

    /**
     * 从持久化重建邀请返利账户聚合（基础设施层映射用）。
     *
     * @param userId          用户 id
     * @param affCode         邀请码（可空）
     * @param affCount        累计邀请人数
     * @param affQuota        当前邀请额度
     * @param affHistoryQuota 历史累计邀请额度
     * @return 重建的聚合
     */
    public static AffiliateAccount rehydrate(long userId, String affCode, long affCount,
                                             long affQuota, long affHistoryQuota) {
        return new AffiliateAccount(userId, affCode, Math.max(affCount, 0L),
                Math.max(affQuota, 0L), Math.max(affHistoryQuota, 0L));
    }

    /**
     * 入账邀请返利（GR-4 §3「邀请人 AffCount++、AffQuota/AffHistoryQuota += QuotaForInviter」，F-1042/F-1043）。
     *
     * <p>领域规则：被邀请人成功归因后，对邀请人执行——邀请人数 +1，当前邀请额度与历史累计邀请额度
     * 各加一份 {@code rewardQuota}（{@code QuotaForInviter}）。{@code affQuota} 可被后续划转消耗，
     * {@code affHistoryQuota} 是只增的历史口径（不随划转回退）。仅在 inviterId 有效时由应用层调用
     * （无效归因不构造/不入账，GR-4 I10-否分支）。</p>
     *
     * @param rewardQuota 单次返利额度（{@code QuotaForInviter}，&gt;= 0）
     * @throws IllegalArgumentException rewardQuota 为负（防御式）
     */
    public void creditInviterReward(long rewardQuota) {
        if (rewardQuota < 0) {
            throw new IllegalArgumentException("inviter reward quota must be non-negative, got " + rewardQuota);
        }
        this.affCount += 1;
        this.affQuota += rewardQuota;
        this.affHistoryQuota += rewardQuota;
    }

    /**
     * 划转邀请额度为可用额度（GR-5 §3「aff_quota -= quota」侧，F-1044）。
     *
     * <p>领域规则与双重前置校验（GR-5 T3/T4，充血守护）：
     * <ol>
     *   <li>{@code amount >= minUnit}（QuotaPerUnit）——否则「转移额度最小为 {minUnit}」拒绝（T3）。</li>
     *   <li>{@code affQuota >= amount}——否则「邀请额度不足」拒绝（T4）。</li>
     * </ol>
     * 校验通过后聚合内 {@code affQuota -= amount}（本聚合只负责扣减自身邀请额度这一侧；对侧
     * {@code users.quota += amount} 的入账由应用层在<b>同一事务</b>内经额度端口完成，保证原子，
     * GR-5 §3「原子执行 aff_quota -= quota、quota += quota」）。{@code payment_compliance} 校验
     * （T5）属应用层前置（外部合规端口），不在本纯领域方法内。{@code affHistoryQuota} 不变
     * （历史口径不因划转回退）。</p>
     *
     * @param amount  划转额度（&gt; 0）
     * @param minUnit 最小划转单位 {@code QuotaPerUnit}（配置传入）
     * @throws AffQuotaTransferException 低于最小单位 / 邀请额度不足
     */
    public void transferToQuota(long amount, long minUnit) {
        if (amount < minUnit) {
            throw new AffQuotaTransferException("转移额度最小为 " + minUnit);
        }
        if (this.affQuota < amount) {
            throw new AffQuotaTransferException("邀请额度不足");
        }
        this.affQuota -= amount;
    }

    /** @return 用户 id */
    public long userId() {
        return userId;
    }

    /** @return 邀请码（可空） */
    public String affCode() {
        return affCode;
    }

    /** @return 累计邀请人数 */
    public long affCount() {
        return affCount;
    }

    /** @return 当前可划转邀请额度 */
    public long affQuota() {
        return affQuota;
    }

    /** @return 历史累计邀请额度 */
    public long affHistoryQuota() {
        return affHistoryQuota;
    }
}
