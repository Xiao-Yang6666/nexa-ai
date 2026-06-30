package com.nexa.application.modelgroup;

import com.nexa.domain.modelgroup.exception.ModelGroupNotFoundException;
import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.repository.ModelGroupRepository;
import com.nexa.domain.modelgroup.vo.AccessPolicy;
import com.nexa.domain.modelgroup.vo.ModelNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import com.nexa.application.modelgroup.command.UpdateModelGroupCommand;

/**
 * 更新模型组用例（管理端）。
 *
 * <p>应用层编排：按 id 取存活聚合（不存在 → 404）→ 解析可选 access_policy → 调聚合充血方法
 * {@link ModelGroup#update}（部分更新，各字段在聚合内校验）→ 持久化。code 不可改（不在更新路径），
 * 故无 code 冲突校验。</p>
 */
@Service
public class UpdateModelGroupUseCase {

    private final ModelGroupRepository repository;

    /**
     * @param repository 模型组仓储
     */
    public UpdateModelGroupUseCase(ModelGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 更新模型组。
     *
     * @param command 更新命令（id 必填；其余非 null 才覆盖）
     * @return 更新后的模型组聚合
     * @throws ModelGroupNotFoundException id 不存在/已删（→404）
     * @throws com.nexa.domain.modelgroup.exception.InvalidModelGroupParameterException 字段非法（→400）
     */
    @Transactional
    public ModelGroup update(UpdateModelGroupCommand command) {
        ModelGroup group = repository.findById(command.id())
                .orElseThrow(() -> new ModelGroupNotFoundException(command.id()));

        // 可选 access_policy：非 null 才解析（解析非法字面量在此抛 400）。
        AccessPolicy policy = command.accessPolicy() == null
                ? null
                : AccessPolicy.fromWire(command.accessPolicy());
        // 可选 models：非 null 才规范化（null 表示不改模型集）。
        ModelNames models = command.models() == null ? null : ModelNames.of(command.models());

        long now = Instant.now().getEpochSecond();
        group.update(command.name(), command.basePriceRatio(), models, policy,
                command.description(), now);
        return repository.save(group);
    }
}
