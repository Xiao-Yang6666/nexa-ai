package com.nexa.modelgroup.infrastructure.pricing;

import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.relay.domain.port.ModelGroupPricingPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 模型组定价端口适配器（modelgroup BC 实现 relay 域定义的 {@link ModelGroupPricingPort}，REQ-05 计费）。
 *
 * <p>依赖倒置落地：relay 域只依赖 {@code ModelGroupPricingPort} 接口，本适配器在 modelgroup BC 内用
 * {@link ModelGroupRepository} 按分组 code 查模型组并返回其售价倍率（{@code ModelGroup.basePriceRatio}）。
 * 这样 relay 不编译期耦合 modelgroup 内部，模型组的查询/软删过滤等细节封装在本 BC。</p>
 *
 * <p>可用性判定：仅<b>启用</b>的模型组返回倍率（禁用组等价未配置，回落兜底）。空模型集组仍返回其倍率
 * （计费维度只关心倍率；模型可用性由选渠/模型解析链路另行判定，不在计费倍率口径内收窄）。
 * code 空白 / 无匹配 / 已软删（仓储 {@code @SQLRestriction} 自动过滤）→ {@link Optional#empty()}。</p>
 */
@Component
public class ModelGroupPricingAdapter implements ModelGroupPricingPort {

    private final ModelGroupRepository modelGroupRepository;

    /**
     * @param modelGroupRepository 模型组仓储
     */
    public ModelGroupPricingAdapter(ModelGroupRepository modelGroupRepository) {
        this.modelGroupRepository = modelGroupRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<BigDecimal> priceRatioOf(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            return Optional.empty();
        }
        return modelGroupRepository.findByCode(groupCode.trim().toLowerCase())
                // 仅启用组参与计费倍率；禁用组回落兜底（status().isEnabled()）。
                .filter(g -> g.status().isEnabled())
                .map(ModelGroup::basePriceRatio)
                .map(r -> r.value());
    }
}
