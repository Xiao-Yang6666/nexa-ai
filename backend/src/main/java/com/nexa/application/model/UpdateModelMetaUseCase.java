package com.nexa.application.model;

import com.nexa.application.model.port.RefreshPricingPort;
import com.nexa.domain.model.exception.InvalidModelParameterException;
import com.nexa.domain.model.exception.ModelMetaNotFoundException;
import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 更新模型元数据用例（应用层，F-3016）。
 *
 * <p>用例编排：id 查存在 → 跨聚合重名校验（排除自身）→ 调聚合方法（status_only 或全量覆盖）
 * → 落库 → RefreshPricing 副作用（PRD ML-1）。状态机/字段保留语义在聚合内（充血，
 * backend-engineer §2.2），本用例只协调外部依赖与跨聚合不变量。</p>
 */
@Service
public class UpdateModelMetaUseCase {

    private final ModelMetaRepository modelRepository;
    private final RefreshPricingPort refreshPricing;

    /**
     * @param modelRepository 模型仓储
     * @param refreshPricing  定价刷新端口
     */
    public UpdateModelMetaUseCase(ModelMetaRepository modelRepository, RefreshPricingPort refreshPricing) {
        this.modelRepository = modelRepository;
        this.refreshPricing = refreshPricing;
    }

    /**
     * 更新模型元数据。
     *
     * @param command 更新命令
     * @return 更新后的模型聚合
     * @throws InvalidModelParameterException 缺 id / 重名 / 字段非法
     * @throws ModelMetaNotFoundException     模型不存在
     */
    @Transactional
    public ModelMeta update(UpdateModelMetaCommand command) {
        if (command.id() == null || command.id() <= 0) {
            // 领域规则来源：F-3016 BACKLOG T-118「Id=0 返回缺少模型 ID」。
            throw new InvalidModelParameterException("缺少模型 ID");
        }
        ModelMeta model = modelRepository.findById(command.id())
                .orElseThrow(() -> new ModelMetaNotFoundException(command.id()));

        if (command.statusOnly()) {
            // status_only：仅改状态，绝不动其他字段（防误清，PRD ML-1）。
            if (command.status() == null) {
                throw new InvalidModelParameterException("status_only 模式下必须提供 status");
            }
            model.updateStatusOnly(command.status());
        } else {
            // 全量覆盖：先做跨聚合重名校验（排除自身），再调聚合覆盖式更新。
            if (command.modelName() != null && !command.modelName().isBlank()) {
                String newName = command.modelName().trim();
                Optional<ModelMeta> sameName = modelRepository.findByModelName(newName);
                if (sameName.isPresent() && !sameName.get().id().equals(model.id())) {
                    // 领域规则来源：F-3016 BACKLOG T-118「重名（排除自身）返回已存在」。
                    throw new InvalidModelParameterException("模型名称已存在");
                }
            }
            model.updateMeta(command.modelName(), command.status(), command.description(),
                    command.icon(), command.tags(), command.vendorId(),
                    command.endpoints(), command.nameRule());
        }

        ModelMeta saved = modelRepository.save(model);
        // 副作用：更新后刷新定价（PRD ML-1）。
        refreshPricing.refresh();
        return saved;
    }
}
