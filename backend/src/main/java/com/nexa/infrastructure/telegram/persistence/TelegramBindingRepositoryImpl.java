package com.nexa.infrastructure.telegram.persistence;

import com.nexa.infrastructure.telegram.persistence.mapper.TelegramBindingMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexa.domain.telegram.exception.TelegramBindingConflictException;
import com.nexa.domain.telegram.model.TelegramBinding;
import com.nexa.domain.telegram.repository.TelegramBindingRepository;
import com.nexa.domain.telegram.vo.TelegramId;
import com.nexa.infrastructure.telegram.persistence.po.TelegramBindingPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 领域仓储 {@link TelegramBindingRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-1051/1052/1054）。
 *
 * <p>DDD 依赖倒置落地：domain 定义 {@code TelegramBindingRepository} 接口，本类用
 * {@link TelegramBindingMapper} + PO 就近工厂方法（{@code PO.of} / {@code po.toDomain}）实现它。
 * 领域实体 {@link TelegramBinding} 与 PO {@link TelegramBindingPO} 分离，domain 因此不感知持久化框架。</p>
 *
 * <p>并发冲突兜底：建绑定并发竞态下，{@code telegram_id} / {@code user_id} 唯一索引在 {@code save}
 * 时抛 {@link DataIntegrityViolationException}，本类翻译为领域语义的 {@link TelegramBindingConflictException}
 * （F-1054 兜底，不吞错）。MyBatis-Plus 同样抛 Spring {@code DataIntegrityViolationException}。</p>
 */
@Repository
public class TelegramBindingRepositoryImpl implements TelegramBindingRepository {

    private final TelegramBindingMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public TelegramBindingRepositoryImpl(TelegramBindingMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TelegramBinding> findByTelegramId(TelegramId telegramId) {
        LambdaQueryWrapper<TelegramBindingPO> w = Wrappers.<TelegramBindingPO>lambdaQuery()
                .eq(TelegramBindingPO::getTelegramId, telegramId.value());
        return Optional.ofNullable(mapper.selectOne(w)).map(TelegramBindingPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TelegramBinding> findByUserId(long userId) {
        LambdaQueryWrapper<TelegramBindingPO> w = Wrappers.<TelegramBindingPO>lambdaQuery()
                .eq(TelegramBindingPO::getUserId, userId);
        return Optional.ofNullable(mapper.selectOne(w)).map(TelegramBindingPO::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws TelegramBindingConflictException 当唯一索引（telegram_id / user_id）冲突时（F-1054 并发兜底）
     */
    @Override
    public TelegramBinding save(TelegramBinding binding) {
        TelegramBindingPO po = TelegramBindingPO.of(binding);
        try {
            mapper.insert(po);          // 回填自增 id 到 po
            // 保存后把数据库生成的 id 回填回领域实体。
            binding.assignId(po.getId());
            return po.toDomain();
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底：并发建绑定竞态或该 Telegram 账号已绑他人，翻译为领域冲突（不回显占用方细节）。
            throw new TelegramBindingConflictException();
        }
    }
}
