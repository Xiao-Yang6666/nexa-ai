package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.exception.ModelGroupNotFoundException;
import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.ModelGroupStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 切换模型组启用/禁用状态用例（管理端）。
 *
 * <p>按 id 取聚合 → 调充血方法 {@link ModelGroup#applyStatus} → 持久化。禁用后中继链路不可选用本组。</p>
 */
@Service
public class UpdateModelGroupStatusUseCase {

    private final ModelGroupRepository repository;

    /**
     * @param repository 模型组仓储
     */
    public UpdateModelGroupStatusUseCase(ModelGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 切换状态。
     *
     * @param id     模型组主键
     * @param status 目标状态整数码（1=启用 2=禁用，脏码归并禁用）
     * @return 更新后的模型组聚合
     * @throws ModelGroupNotFoundException id 不存在/已删（→404）
     */
    @Transactional
    public ModelGroup updateStatus(long id, int status) {
        ModelGroup group = repository.findById(id)
                .orElseThrow(() -> new ModelGroupNotFoundException(id));
        group.applyStatus(ModelGroupStatus.fromCode(status), Instant.now().getEpochSecond());
        return repository.save(group);
    }
}
