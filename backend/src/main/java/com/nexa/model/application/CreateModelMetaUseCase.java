package com.nexa.model.application;

import com.nexa.model.application.port.RefreshPricingPort;
import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.model.ModelMeta;
import com.nexa.model.domain.repository.ModelMetaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建模型元数据用例（应用层，F-3015）。
 *
 * <p>用例编排（事务边界）：名称查重（幂等键 model_name）→ 创建聚合 → 落库 → 触发 RefreshPricing()
 * 副作用（PRD ML-1）。领域不变量（名称非空、长度）在 {@link ModelMeta#create} 内守护，本用例只负责
 * 跨聚合的唯一性校验与副作用编排（backend-engineer §2.2/§5）。</p>
 */
@Service
public class CreateModelMetaUseCase {

    private final ModelMetaRepository modelRepository;
    private final RefreshPricingPort refreshPricing;

    /**
     * @param modelRepository 模型仓储
     * @param refreshPricing  定价刷新端口（创建/更新/删除后触发）
     */
    public CreateModelMetaUseCase(ModelMetaRepository modelRepository, RefreshPricingPort refreshPricing) {
        this.modelRepository = modelRepository;
        this.refreshPricing = refreshPricing;
    }

    /**
     * 创建模型元数据。
     *
     * @param command 创建命令
     * @return 创建后的模型聚合（含自增 id）
     * @throws InvalidModelParameterException 名称为空（领域）或重名（本用例）
     */
    @Transactional
    public ModelMeta create(CreateModelMetaCommand command) {
        ModelMeta model = ModelMeta.create(
                command.modelName(), command.description(), command.icon(), command.tags(),
                command.vendorId(), command.endpoints(), command.nameRule());

        // 跨聚合不变量：model_name 全局唯一（幂等键）。重名 → 「模型名称已存在」（F-3015）。
        modelRepository.findByModelName(model.modelName()).ifPresent(existing -> {
            throw new InvalidModelParameterException("模型名称已存在");
        });

        ModelMeta saved = modelRepository.save(model);
        // 副作用：创建后刷新定价缓存（PRD ML-1）。失败不吞，向上抛（事务回滚）。
        refreshPricing.refresh();
        return saved;
    }
}
