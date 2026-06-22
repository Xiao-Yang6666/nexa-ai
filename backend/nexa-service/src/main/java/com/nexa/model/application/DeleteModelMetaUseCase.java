package com.nexa.model.application;

import com.nexa.model.application.port.RefreshPricingPort;
import com.nexa.model.domain.exception.ModelMetaNotFoundException;
import com.nexa.model.domain.repository.ModelMetaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除模型元数据用例（应用层，F-3017）。
 *
 * <p>用例编排：存在校验 → 软删除 → RefreshPricing 副作用（PRD ML-1）。</p>
 */
@Service
public class DeleteModelMetaUseCase {

    private final ModelMetaRepository modelRepository;
    private final RefreshPricingPort refreshPricing;

    /**
     * @param modelRepository 模型仓储
     * @param refreshPricing  定价刷新端口
     */
    public DeleteModelMetaUseCase(ModelMetaRepository modelRepository, RefreshPricingPort refreshPricing) {
        this.modelRepository = modelRepository;
        this.refreshPricing = refreshPricing;
    }

    /**
     * 按 id 软删除模型。
     *
     * @param id 模型 id
     * @throws ModelMetaNotFoundException 模型不存在
     */
    @Transactional
    public void delete(long id) {
        if (modelRepository.findById(id).isEmpty()) {
            throw new ModelMetaNotFoundException(id);
        }
        modelRepository.deleteById(id);
        // 副作用：删除后刷新定价（PRD ML-1）。
        refreshPricing.refresh();
    }
}
