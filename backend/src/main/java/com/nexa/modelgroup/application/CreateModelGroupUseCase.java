package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.exception.ModelGroupCodeConflictException;
import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import com.nexa.modelgroup.domain.vo.ModelNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 创建模型组用例（管理端）。
 *
 * <p>应用层编排：解析 access_policy 字面量 → 经聚合工厂 {@link ModelGroup#create} 构造（含 name/code/
 * 倍率格式校验）→ <b>code 冲突校验</b>（全局唯一 → 409）→ 持久化。冲突校验需查库（跨聚合约束），属
 * 应用层职责而非聚合自身。事务边界在此，保证「查重 + 入库」原子（并发同 code 由 DB 唯一索引兜底）。</p>
 */
@Service
public class CreateModelGroupUseCase {

    private final ModelGroupRepository repository;

    /**
     * @param repository 模型组仓储
     */
    public CreateModelGroupUseCase(ModelGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建模型组。
     *
     * @param command 创建命令
     * @return 持久化后的模型组聚合（含自增 id）
     * @throws com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException 字段非法（→400）
     * @throws ModelGroupCodeConflictException code 已存在（→409）
     */
    @Transactional
    public ModelGroup create(CreateModelGroupCommand command) {
        AccessPolicy policy = AccessPolicy.fromWire(command.accessPolicy());
        long now = Instant.now().getEpochSecond();

        ModelGroup group = ModelGroup.create(
                command.name(), command.code(), command.basePriceRatio(),
                ModelNames.of(command.models()), policy, command.description(), now);

        if (repository.existsByCode(group.code(), null)) {
            throw new ModelGroupCodeConflictException(group.code());
        }
        return repository.save(group);
    }
}
