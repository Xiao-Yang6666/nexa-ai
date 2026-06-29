package com.nexa.infrastructure.ops.persistence;

import com.nexa.domain.ops.option.Option;
import com.nexa.domain.ops.option.OptionRepository;
import com.nexa.infrastructure.ops.persistence.entity.OptionJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link OptionRepository} 的 JPA 实现（基础设施层适配器，F-4017/F-4018/F-4031）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataOptionJpaRepository} +
 * 实体↔领域映射实现它，domain 因此不感知 Hibernate（backend-engineer §2.3）。覆盖式写入
 * 用 {@code save} 的 merge 语义（存在更新、不存在插入），对齐 F-4018 单键幂等覆盖。</p>
 */
@Repository
public class OptionRepositoryImpl implements OptionRepository {

    private final SpringDataOptionJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public OptionRepositoryImpl(SpringDataOptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public List<Option> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Option> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return jpa.findById(key.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void save(Option option) {
        // merge 语义：key 已存在则更新 value，否则插入（F-4018 覆盖式幂等）。
        jpa.save(new OptionJpaEntity(option.keyName(), option.value()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        // deleteById 在缺失时静默不抛（Spring Data 行为），契合 F-4031 迁移删旧键的幂等需求。
        jpa.deleteById(key.trim());
    }

    private Option toDomain(OptionJpaEntity e) {
        return Option.of(e.getKey(), e.getValue());
    }
}
