package com.nexa.infrastructure.telegram.persistence;

import com.nexa.domain.telegram.exception.TelegramBindingConflictException;
import com.nexa.domain.telegram.model.TelegramBinding;
import com.nexa.domain.telegram.repository.TelegramBindingRepository;
import com.nexa.domain.telegram.vo.TelegramId;
import com.nexa.infrastructure.telegram.persistence.entity.TelegramBindingJpaEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 领域仓储 {@link TelegramBindingRepository} 的 JPA 实现（基础设施层适配器，F-1051/1052/1054）。
 *
 * <p>DDD 依赖倒置落地：domain 定义 {@code TelegramBindingRepository} 接口，本类用
 * {@link SpringDataTelegramBindingJpaRepository} + 实体↔领域映射实现它（backend-engineer §2.3）。
 * 领域实体 {@link TelegramBinding} 与 JPA 实体 {@link TelegramBindingJpaEntity} 分离，映射集中于此，
 * domain 因此不感知 Hibernate。</p>
 *
 * <p>并发冲突兜底：建绑定并发竞态下，{@code telegram_id} / {@code user_id} 唯一索引在 {@code save}
 * 时抛 {@link DataIntegrityViolationException}，本类翻译为领域语义的 {@link TelegramBindingConflictException}
 * （F-1054 兜底，不吞错）。</p>
 */
@Repository
public class TelegramBindingRepositoryImpl implements TelegramBindingRepository {

    private final SpringDataTelegramBindingJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public TelegramBindingRepositoryImpl(SpringDataTelegramBindingJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TelegramBinding> findByTelegramId(TelegramId telegramId) {
        return jpa.findByTelegramId(telegramId.value())
                .map(TelegramBindingRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TelegramBinding> findByUserId(long userId) {
        return jpa.findByUserId(userId)
                .map(TelegramBindingRepositoryImpl::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws TelegramBindingConflictException 当唯一索引（telegram_id / user_id）冲突时（F-1054 并发兜底）
     */
    @Override
    public TelegramBinding save(TelegramBinding binding) {
        TelegramBindingJpaEntity entity = toEntity(binding);
        try {
            TelegramBindingJpaEntity saved = jpa.saveAndFlush(entity);
            // 保存后把数据库生成的 id 回填回领域实体。
            binding.assignId(saved.getId());
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底：并发建绑定竞态或该 Telegram 账号已绑他人，翻译为领域冲突（不回显占用方细节）。
            throw new TelegramBindingConflictException();
        }
    }

    // ---- 领域实体 <-> JPA 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域实体 → JPA 实体（持久化方向）。
     *
     * @param binding 领域绑定实体
     * @return 待持久化的 JPA 实体
     */
    private static TelegramBindingJpaEntity toEntity(TelegramBinding binding) {
        TelegramBindingJpaEntity e = new TelegramBindingJpaEntity();
        e.setId(binding.id());
        e.setUserId(binding.userId());
        e.setTelegramId(binding.telegramId().value());
        e.setCreatedAt(binding.createdAt() == null
                ? Instant.now().getEpochSecond()
                : binding.createdAt().getEpochSecond());
        return e;
    }

    /**
     * JPA 实体 → 领域实体（重建方向，走 {@link TelegramBinding#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的领域绑定实体
     */
    private static TelegramBinding toDomain(TelegramBindingJpaEntity e) {
        Instant createdAt = e.getCreatedAt() == null
                ? Instant.now()
                : Instant.ofEpochSecond(e.getCreatedAt());
        return TelegramBinding.rehydrate(
                e.getId(),
                e.getUserId(),
                TelegramId.of(e.getTelegramId()),
                createdAt);
    }
}
