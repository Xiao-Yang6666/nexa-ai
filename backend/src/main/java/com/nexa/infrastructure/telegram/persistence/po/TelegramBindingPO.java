package com.nexa.infrastructure.telegram.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Telegram 绑定 JPA 持久化实体（基础设施层，表 {@code telegram_bindings}）。
 *
 * <p>本实体是<b>持久化映射</b>，与领域实体 {@link com.nexa.domain.telegram.model.TelegramBinding} 分离
 * （DDD：domain 不感知 JPA）。映射转换在 {@code TelegramBindingRepositoryImpl}。</p>
 *
 * <p>设计说明：Telegram 登录走独立 HMAC 路径（非标准 OAuth），故不复用 {@code user_oauth_bindings} 表，
 * 用独立绑定表承载 telegram_id ↔ user_id。{@code telegram_id} 唯一索引落实 F-1054「一 Telegram 账号
 * 一本站账号」；{@code user_id} 唯一索引落实「一本站账号至多绑一个 Telegram」（与 User.telegram_id 单列语义一致）。
 * DB-SCHEMA §1 在 users 上有 telegram_id 索引列；本切片以独立绑定表实现，便于反查与唯一性兜底，
 * 是对「telegram_id 直接落 users 列」的合理替代（与 OAuthBinding 独立建模同理，已在 OAuthProvider 注释说明 Telegram 走独立路径）。</p>
 */
@Entity
@Table(name = "telegram_bindings",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_telegram_id", columnNames = {"telegram_id"}),
                @UniqueConstraint(name = "ux_telegram_user_id", columnNames = {"user_id"})
        },
        indexes = {
                @Index(name = "idx_telegram_bindings_user_id", columnList = "user_id")
        })
public class TelegramBindingPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 绑定归属的本站用户 id（not null；逻辑外键 → users.id）。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 绑定的 Telegram 账号 id（数字串，not null，唯一）。 */
    @Column(name = "telegram_id", nullable = false, length = 64)
    private String telegramId;

    /** 绑定建立时间 epoch 秒。 */
    @Column(name = "created_at")
    private Long createdAt;

    /** JPA 规范要求的无参构造器。 */
    public TelegramBindingPO() {
    }

    // ---- 访问器（JPA 需要 getter/setter；映射在 RepositoryImpl，领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
