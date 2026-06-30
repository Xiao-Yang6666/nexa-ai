package com.nexa.domain.account.twofa.model;

import com.nexa.domain.account.twofa.vo.BackupCodeHasher;

import java.time.Instant;
import java.util.Objects;

/**
 * 双因子备份码实体（F-1035，对齐 DB-SCHEMA §15 {@code two_fa_backup_codes}）。
 *
 * <p>属 {@link TwoFA} 聚合下的子实体（一个 2FA 配置对应一批备份码）。每条以哈希存储应急登录码，
 * 一次性：被消费后置 {@code used}/{@code usedAt}，不可再用（防重放）。明文绝不落库、绝不进视图
 * （仅生成时一次性返回用户）。</p>
 *
 * <p>充血：消费动作 {@link #consume(Instant)} 守护"已用不可再消费"不变量（backend-engineer §2.2）。
 * 零框架依赖，与 JPA 实体分离可纯单测。</p>
 */
public class TwoFABackupCode {

    /** 落库哈希长度上限，对齐 DB-SCHEMA §15 {@code code_hash varchar(255)}。 */
    public static final int CODE_HASH_MAX_LENGTH = 255;

    /** 自增主键，未持久化为 null。 */
    private Long id;

    /** 归属用户 id（逻辑外键 → users.id；与 {@link TwoFA#userId()} 一致）。 */
    private final long userId;

    /** 备份码哈希（敏感，绝不下发视图）。 */
    private final String codeHash;

    /** 是否已被消费。 */
    private boolean used;

    /** 消费时间（未消费为 null）。 */
    private Instant usedAt;

    private TwoFABackupCode(Long id, long userId, String codeHash, boolean used, Instant usedAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.used = used;
        this.usedAt = usedAt;
    }

    /**
     * 工厂：用明文码的哈希新建一条未使用备份码（F-1035 生成）。
     *
     * @param userId   归属用户 id
     * @param codeHash 备份码哈希（由 {@link BackupCodeHasher} 产出）
     * @return 待持久化的备份码实体
     */
    public static TwoFABackupCode issue(long userId, String codeHash) {
        Objects.requireNonNull(codeHash, "codeHash");
        if (codeHash.length() > CODE_HASH_MAX_LENGTH) {
            throw new IllegalArgumentException("code hash length must be <= " + CODE_HASH_MAX_LENGTH);
        }
        return new TwoFABackupCode(null, userId, codeHash, false, null);
    }

    /**
     * 基础设施层持久化重建专用工厂（不触发校验）。
     *
     * @param id       主键
     * @param userId   归属用户 id
     * @param codeHash 哈希
     * @param used     是否已用
     * @param usedAt   消费时间（可空）
     * @return 重建实体
     */
    public static TwoFABackupCode rehydrate(Long id, long userId, String codeHash, boolean used, Instant usedAt) {
        return new TwoFABackupCode(id, userId, codeHash, used, usedAt);
    }

    /**
     * 消费本备份码（应急登录命中时，充血行为）。
     *
     * <p>不变量：已消费的码不可再次消费（防重放——一次性语义）。</p>
     *
     * @param at 消费时间
     * @return 本次成功消费返回 {@code true}；若已被消费返回 {@code false}（不抛异常，便于调用方按"无可用码"处理）
     */
    public boolean consume(Instant at) {
        if (used) {
            return false;
        }
        this.used = true;
        this.usedAt = at;
        return true;
    }

    /**
     * 用给定哈希器判断明文是否匹配本码哈希（不改变状态）。
     *
     * @param rawCode 明文备份码
     * @param hasher  哈希器端口
     * @return 匹配返回 {@code true}
     */
    public boolean matches(String rawCode, BackupCodeHasher hasher) {
        return hasher.matches(rawCode, codeHash);
    }

    /** 由仓储在保存后回填主键。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 归属用户 id */
    public long userId() {
        return userId;
    }

    /** @return 备份码哈希（敏感，仅基础设施层持久化用，绝不下发视图） */
    public String codeHash() {
        return codeHash;
    }

    /** @return 是否已被消费 */
    public boolean used() {
        return used;
    }

    /** @return 消费时间，未消费为 null */
    public Instant usedAt() {
        return usedAt;
    }
}
