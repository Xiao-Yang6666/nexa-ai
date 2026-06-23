package com.nexa.telegram.infrastructure.persistence;

import com.nexa.telegram.infrastructure.persistence.entity.TelegramBindingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA 仓库（Telegram 绑定，基础设施层内部接口）。
 *
 * <p>仅供 {@link TelegramBindingRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.TelegramBindingRepository}。派生查询对齐唯一约束
 * {@code ux_telegram_id (telegram_id)} 与 {@code ux_telegram_user_id (user_id)}。</p>
 */
interface SpringDataTelegramBindingJpaRepository extends JpaRepository<TelegramBindingJpaEntity, Long> {

    /**
     * 按 telegram_id 反查绑定（Telegram 登录核心查询 F-1051 / 绑定唯一性判定 F-1054）。
     *
     * @param telegramId Telegram 账号 id 串
     * @return 命中实体，否则空（唯一，至多一条）
     */
    Optional<TelegramBindingJpaEntity> findByTelegramId(String telegramId);

    /**
     * 按 user_id 查绑定（本站账号是否已绑 Telegram）。
     *
     * @param userId 本站用户 id
     * @return 命中实体，否则空（唯一，至多一条）
     */
    Optional<TelegramBindingJpaEntity> findByUserId(Long userId);
}
