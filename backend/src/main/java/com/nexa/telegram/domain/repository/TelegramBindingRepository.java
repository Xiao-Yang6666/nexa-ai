package com.nexa.telegram.domain.repository;

import com.nexa.telegram.domain.model.TelegramBinding;
import com.nexa.telegram.domain.vo.TelegramId;

import java.util.Optional;

/**
 * Telegram 绑定仓储接口（domain 定义，infrastructure 实现 —— DDD 依赖倒置，backend-engineer §2.3）。
 *
 * <p>领域/应用层只依赖本接口，不感知 JPA/Hibernate；实现见
 * {@code com.nexa.telegram.infrastructure.persistence.TelegramBindingRepositoryImpl}。
 * 支撑 F-1051（按 telegram_id 反查归属用户登录）与 F-1052/F-1054（建绑定 + 唯一性兜底）。</p>
 */
public interface TelegramBindingRepository {

    /**
     * 按 Telegram 账号 id 反查绑定（Telegram 登录核心查询，F-1051）。
     *
     * @param telegramId Telegram 账号 id 值对象
     * @return 命中的绑定，或空
     */
    Optional<TelegramBinding> findByTelegramId(TelegramId telegramId);

    /**
     * 查某用户是否已有 Telegram 绑定（绑定幂等/归属判定辅助）。
     *
     * @param userId 本站用户 id
     * @return 命中的绑定，或空
     */
    Optional<TelegramBinding> findByUserId(long userId);

    /**
     * 保存绑定（新建回填 id）。
     *
     * @param binding 待保存绑定
     * @return 持久化后的绑定（含 id）
     * @throws com.nexa.telegram.domain.exception.TelegramBindingConflictException
     *         当唯一索引（telegram_id 唯一）冲突时（F-1054 并发兜底）
     */
    TelegramBinding save(TelegramBinding binding);
}
