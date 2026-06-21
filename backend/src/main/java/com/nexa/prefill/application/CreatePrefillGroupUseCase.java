package com.nexa.prefill.application;

import com.nexa.prefill.domain.exception.PrefillGroupNameConflictException;
import com.nexa.prefill.domain.model.PrefillGroup;
import com.nexa.prefill.domain.repository.PrefillGroupRepository;
import com.nexa.prefill.domain.vo.PrefillItems;
import com.nexa.prefill.domain.vo.PrefillType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 创建预填分组用例（F-2012，PRD 模块十五 §14）。
 *
 * <p>应用层编排：解析 type 字面量 → 经聚合工厂 {@link PrefillGroup#create} 构造（含 name/type
 * 格式校验）→ <b>名称冲突校验</b>（同 type 下重名 → 409）→ 持久化。冲突校验需查库（跨多个分组的
 * 约束），属应用层职责而非聚合自身（聚合只守自身不变量，backend-engineer §2.2）。事务边界在此，
 * 保证「查重 + 入库」原子（并发同名最终由 DB 唯一索引 uk_prefill_name 兜底）。</p>
 */
@Service
public class CreatePrefillGroupUseCase {

    private final PrefillGroupRepository repository;

    /**
     * @param repository 预填分组仓储
     */
    public CreatePrefillGroupUseCase(PrefillGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建预填分组。
     *
     * @param command 创建命令（名称/类型/条目/描述）
     * @return 持久化后的分组聚合（含自增 id）
     * @throws com.nexa.prefill.domain.exception.InvalidPrefillParameterException name/type 非法（→400）
     * @throws PrefillGroupNameConflictException 同 type 下已存在同名分组（→409）
     */
    @Transactional
    public PrefillGroup create(CreatePrefillGroupCommand command) {
        // type 字面量解析（非法枚举在此抛 InvalidPrefillParameterException → 400）。
        PrefillType type = PrefillType.fromWire(command.type());
        long now = Instant.now().getEpochSecond();

        // 聚合工厂校验 name 格式 + 规范化条目（充血：领域规则在聚合内）。
        PrefillGroup group = PrefillGroup.create(
                command.name(), type, PrefillItems.of(command.items()), command.description(), now);

        // 名称冲突校验：同 type 下是否已有同名存活分组（创建时无自身需排除，PRD §14 名称冲突校验）。
        if (repository.existsByTypeAndName(type, group.name(), null)) {
            throw new PrefillGroupNameConflictException(group.name());
        }
        return repository.save(group);
    }
}
