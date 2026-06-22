package com.nexa.growth.infrastructure.persistence;

import com.nexa.growth.domain.exception.GrowthPersistenceException;
import com.nexa.growth.domain.exception.GrowthUserNotFoundException;
import com.nexa.growth.domain.model.AffiliateAccount;
import com.nexa.growth.domain.repository.AffiliateAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邀请返利账户仓储 {@link AffiliateAccountRepository} 的 JDBC 实现（基础设施层适配器，PRD GR-4/GR-5）。
 *
 * <p><b>跨 bounded context 防腐适配器</b>：邀请返利状态是 {@code users} 表 aff_* 列（{@code aff_code/
 * aff_count/aff_quota/aff_history/inviter_id}，DB-SCHEMA §1）的投影，而 {@code users} 表归账号域。增长域
 * 用本 JDBC 适配器只触碰 aff_* 列读写——不复用账号域 {@code UserJpaEntity}（避免 context 耦合与重复
 * 映射同表）。与计费域 {@code JdbcUserQuotaAccount}、本域 {@code JdbcUserQuotaAccount} 同策略。</p>
 *
 * <p>原子写：返利入账与额度扣减用 SQL 级原子自增/条件 UPDATE（CAS），杜绝并发丢更新 / 超额扣减。
 * 唯一码生成用 {@code idx_users_aff_code} 唯一索引兜底，冲突重试。</p>
 */
@Repository
public class JdbcAffiliateAccountRepository implements AffiliateAccountRepository {

    /** 邀请码长度（4 位，对齐 PRD GR-4 / DB-SCHEMA §1 aff_code）。 */
    private static final int AFF_CODE_LENGTH = 4;

    /** 邀请码字符集（去掉易混 0/O/1/I，与账号域 User.generateAffCode 保持一致风格）。 */
    private static final char[] AFF_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** 生成唯一码的最大重试次数（4 位 32 字符集空间约 100 万，冲突概率极低，少量重试足够）。 */
    private static final int MAX_AFF_CODE_ATTEMPTS = 8;

    private final JdbcTemplate jdbcTemplate;

    /** @param jdbcTemplate Spring JDBC 模板（由 JPA starter 数据源自动装配） */
    public JdbcAffiliateAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<AffiliateAccount> findByUserId(long userId) {
        // 只读 aff_* 列；软删除用户视为不存在。
        return Optional.ofNullable(jdbcTemplate.query(
                "SELECT aff_code, aff_count, aff_quota, aff_history FROM users "
                        + "WHERE id = ? AND deleted_at IS NULL",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String affCode = rs.getString("aff_code");
                    long affCount = rs.getLong("aff_count");
                    long affQuota = rs.getLong("aff_quota");
                    long affHistory = rs.getLong("aff_history");
                    return AffiliateAccount.rehydrate(userId, affCode, affCount, affQuota, affHistory);
                },
                userId));
    }

    /** {@inheritDoc} */
    @Override
    public String getOrCreateAffCode(long userId) {
        String existing = jdbcTemplate.query(
                "SELECT aff_code FROM users WHERE id = ? AND deleted_at IS NULL",
                rs -> rs.next() ? rs.getString(1) : Boolean.FALSE.toString(), // 区分「无此用户」与「码为 null」
                userId);
        if (existing == null) {
            // 用户存在但 aff_code 为 NULL：进入生成分支。
            return generateAndPersistAffCode(userId);
        }
        if (Boolean.FALSE.toString().equals(existing)) {
            // 哨兵：查询无行 = 用户不存在/已删。
            throw new GrowthUserNotFoundException(userId);
        }
        if (existing.isBlank()) {
            return generateAndPersistAffCode(userId);
        }
        return existing; // GR-4 I3：已有码原样返回
    }

    /**
     * 生成全局唯一的 4 位邀请码并落库（GR-4 I2）。冲突（命中唯一索引）则重试。
     *
     * @param userId 用户 id
     * @return 落库成功的唯一邀请码
     * @throws GrowthPersistenceException 多次重试仍无法生成唯一码（极端碰撞，防御式）
     * @throws GrowthUserNotFoundException 目标用户不存在/已删
     */
    private String generateAndPersistAffCode(long userId) {
        for (int attempt = 0; attempt < MAX_AFF_CODE_ATTEMPTS; attempt++) {
            String candidate = randomAffCode();
            try {
                // 仅当当前 aff_code 仍为空时才写（避免并发覆盖已生成的码）；唯一索引保证全局不撞。
                int affected = jdbcTemplate.update(
                        "UPDATE users SET aff_code = ? "
                                + "WHERE id = ? AND deleted_at IS NULL AND (aff_code IS NULL OR aff_code = '')",
                        candidate, userId);
                if (affected == 1) {
                    return candidate; // 本次写入成功
                }
                // affected==0：要么用户不存在，要么已被并发生成 → 回读现值。
                String now = jdbcTemplate.query(
                        "SELECT aff_code FROM users WHERE id = ? AND deleted_at IS NULL",
                        rs -> rs.next() ? rs.getString(1) : null,
                        userId);
                if (now == null) {
                    throw new GrowthUserNotFoundException(userId);
                }
                if (!now.isBlank()) {
                    return now; // 并发已生成，返回现值（GR-4「再次进入返回同一码」）
                }
                // 仍为空（罕见竞态）→ 继续重试
            } catch (DataIntegrityViolationException dup) {
                // 撞 idx_users_aff_code 唯一索引 → 换一个候选码重试。
            }
        }
        throw new GrowthPersistenceException(
                "failed to generate a unique aff_code after " + MAX_AFF_CODE_ATTEMPTS + " attempts for userId=" + userId,
                null);
    }

    /** {@inheritDoc} */
    @Override
    public boolean creditInviterReward(long userId, long rewardQuota) {
        // 原子自增：邀请人数 +1，当前/历史邀请额度各 += rewardQuota（GR-4 I13/I14）。
        int affected = jdbcTemplate.update(
                "UPDATE users SET aff_count = aff_count + 1, "
                        + "aff_quota = aff_quota + ?, aff_history = aff_history + ? "
                        + "WHERE id = ? AND deleted_at IS NULL",
                rewardQuota, rewardQuota, userId);
        return affected == 1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean deductAffQuota(long userId, long amount) {
        // CAS 条件扣减：余额充足才扣（WHERE aff_quota >= amount），杜绝并发超额扣减（GR-5 §3）。
        int affected = jdbcTemplate.update(
                "UPDATE users SET aff_quota = aff_quota - ? "
                        + "WHERE id = ? AND deleted_at IS NULL AND aff_quota >= ?",
                amount, userId, amount);
        return affected == 1;
    }

    /**
     * 生成一个 {@value #AFF_CODE_LENGTH} 位随机邀请码（字符集去除易混字符）。
     *
     * @return 随机邀请码
     */
    private String randomAffCode() {
        StringBuilder sb = new StringBuilder(AFF_CODE_LENGTH);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < AFF_CODE_LENGTH; i++) {
            sb.append(AFF_CODE_ALPHABET[rnd.nextInt(AFF_CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }
}
