package com.nexa.infrastructure.telegram.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.telegram.model.TelegramBinding;
import com.nexa.domain.telegram.vo.TelegramId;

import java.time.Instant;

/**
 * Telegram 绑定 JPA 持久化实体（基础设施层，表 {@code telegram_bindings}）。
 *
 * <p>本实体是<b>持久化映射</b>，与领域实体 {@link com.nexa.domain.telegram.model.TelegramBinding} 分离
 * （DDD：domain 不感知 JPA）。映射由本类就近工厂方法 {@link #toDomain()} / {@link #of(TelegramBinding)} 承载。</p>
 *
 * <p>设计说明：Telegram 登录走独立 HMAC 路径（非标准 OAuth），故不复用 {@code user_oauth_bindings} 表，
 * 用独立绑定表承载 telegram_id ↔ user_id。{@code telegram_id} 唯一索引落实 F-1054「一 Telegram 账号
 * 一本站账号」；{@code user_id} 唯一索引落实「一本站账号至多绑一个 Telegram」（与 User.telegram_id 单列语义一致）。
 * DB-SCHEMA §1 在 users 上有 telegram_id 索引列；本切片以独立绑定表实现，便于反查与唯一性兜底，
 * 是对「telegram_id 直接落 users 列」的合理替代（与 OAuthBinding 独立建模同理，已在 OAuthProvider 注释说明 Telegram 走独立路径）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("telegram_bindings")
public class TelegramBindingPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 绑定归属的本站用户 id（not null；逻辑外键 → users.id）。 */
    @TableField("user_id")
    private Long userId;

    /** 绑定的 Telegram 账号 id（数字串，not null，唯一）。 */
    @TableField("telegram_id")
    private String telegramId;

    /** 绑定建立时间 epoch 秒。 */
    @TableField("created_at")
    private Long createdAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public TelegramBindingPO() {
    }

    // ---- 访问器（框架需要 getter/setter；映射在就近工厂方法，领域逻辑不在此） ----

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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * PO → 领域实体（重建方向，走 {@link TelegramBinding#rehydrate}）：{@code createdAt} 为空兜底为当前时刻。
     *
     * @return 重建的领域绑定实体
     */
    public TelegramBinding toDomain() {
        Instant createdAtInstant = createdAt == null
                ? Instant.now()
                : Instant.ofEpochSecond(createdAt);
        return TelegramBinding.rehydrate(
                id,
                userId,
                TelegramId.of(telegramId),
                createdAtInstant);
    }

    /**
     * 领域实体 → PO（持久化方向）：逐字段映射，{@code createdAt} 为空兜底为当前 epoch 秒。
     *
     * @param binding 领域绑定实体（非空）
     * @return 待持久化的 PO
     */
    public static TelegramBindingPO of(TelegramBinding binding) {
        TelegramBindingPO e = new TelegramBindingPO();
        e.id = binding.id();
        e.userId = binding.userId();
        e.telegramId = binding.telegramId().value();
        e.createdAt = binding.createdAt() == null
                ? Instant.now().getEpochSecond()
                : binding.createdAt().getEpochSecond();
        return e;
    }
}
