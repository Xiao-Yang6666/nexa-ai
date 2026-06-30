package com.nexa.infrastructure.ops.persistence;

import com.nexa.domain.ops.option.Option;
import com.nexa.domain.ops.option.OptionRepository;
import com.nexa.infrastructure.ops.persistence.po.OptionPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link OptionRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-4017/F-4018/F-4031）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link OptionMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现它，domain 因此不感知持久化框架。覆盖式写入用
 * 「先查后写」的 merge 语义（存在更新、不存在插入），对齐 F-4018 单键幂等覆盖。</p>
 */
@Repository
public class OptionRepositoryImpl implements OptionRepository {

    private final OptionMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public OptionRepositoryImpl(OptionMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public List<Option> findAll() {
        return mapper.selectList(null).stream().map(OptionPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Option> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(key.trim())).map(OptionPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void save(Option option) {
        // merge 语义：key 已存在则更新 value，否则插入（F-4018 覆盖式幂等）。
        OptionPO po = OptionPO.of(option);
        if (mapper.selectById(po.getKey()) != null) {
            mapper.updateById(po);
        } else {
            mapper.insert(po);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        // deleteById 在缺失时静默不抛（影响 0 行），契合 F-4031 迁移删旧键的幂等需求。
        mapper.deleteById(key.trim());
    }
}
