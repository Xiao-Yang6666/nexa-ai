package com.nexa.telegram.domain.model;

import com.nexa.telegram.domain.exception.InvalidTelegramAuthException;
import com.nexa.telegram.domain.exception.TelegramBindingConflictException;
import com.nexa.telegram.domain.vo.TelegramId;

import java.time.Instant;
import java.util.Objects;

/**
 * Telegram 绑定实体（充血领域模型，F-1052/F-1054）。
 *
 * <p>表示「某本站用户 ↔ 某 Telegram 账号」的一条绑定关系。Telegram 登录走独立的 HMAC 校验路径
 * （非标准 OAuth 授权码流程，见 {@code com.nexa.account.OAuthProvider} 注释），故不复用 OAuth 绑定表，
 * 而以独立绑定表 {@code telegram_bindings} 建模——便于按 {@code telegram_id} 反查归属用户
 * （Telegram 登录核心查询），并承载「一 Telegram 账号一本站账号」唯一性（F-1054）。</p>
 *
 * <p>充血而非贫血（backend-engineer §2.2）：绑定不变量（user/telegramId 非空、归属唯一性校验）
 * 在本实体方法上守护，应用层只编排。本类零框架依赖（不 import JPA/Spring），与 JPA 实体分离，可纯单测。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>{@code userId} 必为正（已持久化用户）、{@code telegramId} 非空。</li>
 *   <li>{@code telegramId} 一旦建立不可变（身份锚点）。</li>
 *   <li>每个 telegram_id 至多绑定一个本站账号（唯一性最终由 DB 唯一索引兜底，
 *       本实体在 {@link #ensureOwnedBy} 提供应用层可调的归属校验，F-1054）。</li>
 * </ul></p>
 */
public class TelegramBinding {

    /** 自增主键，未持久化的新绑定为 null。 */
    private Long id;

    /** 绑定归属的本站用户 id（not null，逻辑外键 → users.id）。 */
    private final long userId;

    /** 绑定的 Telegram 账号 id（not null，唯一）。 */
    private final TelegramId telegramId;

    /** 绑定建立时间。 */
    private final Instant createdAt;

    private TelegramBinding(Long id, long userId, TelegramId telegramId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.telegramId = telegramId;
        this.createdAt = createdAt;
    }

    /**
     * 创建一条新的 Telegram 绑定（工厂方法，充血行为，校验不变量，F-1052）。
     *
     * @param userId     绑定归属用户 id（须 &gt; 0，已持久化用户）
     * @param telegramId Telegram 账号 id 值对象（非空）
     * @return 待持久化的新绑定（id 由仓储保存后回填）
     * @throws InvalidTelegramAuthException 当 userId 非正时
     */
    public static TelegramBinding create(long userId, TelegramId telegramId) {
        if (userId <= 0L) {
            throw new InvalidTelegramAuthException("telegram binding requires a persisted user id");
        }
        Objects.requireNonNull(telegramId, "telegramId");
        return new TelegramBinding(null, userId, telegramId, Instant.now());
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配实体（不触发创建不变量与时间打点）。
     *
     * @param id         主键
     * @param userId     归属用户 id
     * @param telegramId Telegram 账号 id
     * @param createdAt  绑定建立时间
     * @return 重建的绑定实体
     */
    public static TelegramBinding rehydrate(Long id, long userId, TelegramId telegramId, Instant createdAt) {
        return new TelegramBinding(id, userId, telegramId, createdAt);
    }

    /**
     * 校验本绑定归属于指定用户，否则冲突（绑定唯一性护栏，F-1054）。
     *
     * <p>领域规则（BACKLOG T-054）：绑定流程中，若据 {@code telegram_id} 反查到的绑定已属于
     * <b>另一个</b>用户，则当前用户不能再绑同一 Telegram 账户（违反「一 Telegram 账号一本站账号」）。
     * 归属一致则幂等通过（同一用户重复绑定同一 Telegram 不报错）。</p>
     *
     * @param candidateUserId 期望的归属用户 id
     * @throws TelegramBindingConflictException 当本绑定归属另一用户时
     */
    public void ensureOwnedBy(long candidateUserId) {
        if (this.userId != candidateUserId) {
            // 不回显占用方 userId（防账号枚举），抛稳定冲突语义。
            throw new TelegramBindingConflictException();
        }
    }

    /**
     * 由仓储在保存后回填数据库主键。
     *
     * @param id 数据库自增主键
     */
    public void assignId(Long id) {
        this.id = id;
    }

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 绑定归属用户 id */
    public long userId() {
        return userId;
    }

    /** @return 绑定的 Telegram 账号 id 值对象 */
    public TelegramId telegramId() {
        return telegramId;
    }

    /** @return 绑定建立时间 */
    public Instant createdAt() {
        return createdAt;
    }
}
