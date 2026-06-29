package com.nexa.domain.growth.repository;

import com.nexa.domain.growth.model.AffiliateAccount;

import java.util.Optional;

/**
 * 邀请返利账户仓储接口（领域层定义，基础设施层实现，PRD GR-4/GR-5）。
 *
 * <p>读写 {@code users} 表 aff_* 列在增长 bounded context 内的投影聚合 {@link AffiliateAccount}。
 * 与计费域 {@code UserQuotaAccount} 同策略：增长域不反向依赖账号域 User 聚合/JPA 实体，用本仓储的
 * 实现（独立 JDBC/JPA 适配器，仅触碰 aff_* 列）完成跨域协作（backend-engineer §2.3 依赖倒置 +
 * bounded context 解耦）。</p>
 */
public interface AffiliateAccountRepository {

    /**
     * 按用户 id 读取邀请返利账户投影（GR-4 入账前读、GR-5 划转前读、邀请统计展示读）。
     *
     * @param userId 用户 id
     * @return 命中返回投影聚合，目标用户不存在/已软删除返回空
     */
    Optional<AffiliateAccount> findByUserId(long userId);

    /**
     * 读取或生成本人邀请码（GR-4 {@code GET /self/aff}，F-1039）。
     *
     * <p>语义（GR-4 I1/I2/I3）：用户已有 {@code aff_code} 则原样返回；无则生成一个<b>全局唯一</b>的
     * 4 位随机码落库并返回（再次进入返回同一个码）。唯一性由 {@code idx_users_aff_code} 唯一索引兜底，
     * 实现内重试至生成不冲突的码。本能力放仓储是因为「生成唯一码」需要查重（IO）。</p>
     *
     * @param userId 用户 id
     * @return 该用户的邀请码（已有则返回原码，无则新生成的 4 位码）
     */
    String getOrCreateAffCode(long userId);

    /**
     * 原子入账邀请返利（GR-4 §3，F-1042/F-1043）。
     *
     * <p>把聚合 {@link AffiliateAccount#creditInviterReward} 计算后的新值落库。实现须用 SQL 级原子
     * 自增（{@code aff_count = aff_count + 1, aff_quota = aff_quota + ?, aff_history = aff_history + ?}）
     * 而非读-改-写，杜绝并发归因丢更新。</p>
     *
     * @param userId      邀请人 id
     * @param rewardQuota 单次返利额度（{@code QuotaForInviter}）
     * @return 入账成功（影响 1 行）返回 {@code true}；用户不存在/已删返回 {@code false}
     */
    boolean creditInviterReward(long userId, long rewardQuota);

    /**
     * 原子扣减邀请额度（GR-5 §3 {@code aff_quota -= quota} 侧，F-1044）。
     *
     * <p>实现须用条件 UPDATE（{@code SET aff_quota = aff_quota - ? WHERE id=? AND aff_quota >= ?}）做
     * CAS 守卫，保证并发划转下「余额充足才扣」且仅扣一次（返回受影响行数）。对侧
     * {@code users.quota += amount} 入账由调用方在同一事务内经额度端口完成（原子）。</p>
     *
     * @param userId 用户 id
     * @param amount 划转额度
     * @return 扣减成功（余额足够、影响 1 行）返回 {@code true}；余额不足/用户不存在返回 {@code false}
     */
    boolean deductAffQuota(long userId, long amount);
}
