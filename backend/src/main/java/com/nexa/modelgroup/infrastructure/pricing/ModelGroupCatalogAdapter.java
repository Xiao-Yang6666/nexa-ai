package com.nexa.modelgroup.infrastructure.pricing;

import com.nexa.model.application.port.ModelGroupCatalogPort;
import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 价格分组目录端口适配器（modelgroup BC 实现 model 域定义的 {@link ModelGroupCatalogPort}，公开价格页对比）。
 *
 * <p>依赖倒置落地：model 域只依赖 {@code ModelGroupCatalogPort} 接口，本适配器在 modelgroup BC 内用
 * {@link ModelGroupRepository#findAll()} 取全量分组，过滤出<b>启用 + 公开（PUBLIC）</b>分组，按各组的可用
 * 模型集反查「模型名 → 可见分组」映射。这样 model 不编译期耦合 modelgroup 内部，分组的查询/软删过滤
 * （仓储 {@code @SQLRestriction}）/可见性细节封装在本 BC。</p>
 *
 * <p><b>零泄露 + 公开口径</b>：仅 PUBLIC 策略的启用分组进入公开价格页对比（PRIVATE/AUTO_LEVEL 是内部分层
 * 定价，匿名公开页不暴露）；返回的 {@link GroupPricing} 只含展示名/编码/倍率，无成本/利润/B/渠道。</p>
 */
@Component
public class ModelGroupCatalogAdapter implements ModelGroupCatalogPort {

    private final ModelGroupRepository modelGroupRepository;

    /** @param modelGroupRepository 模型组仓储 */
    public ModelGroupCatalogAdapter(ModelGroupRepository modelGroupRepository) {
        this.modelGroupRepository = modelGroupRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, List<GroupPricing>> visibleGroupsByModel() {
        // LinkedHashMap 保稳定顺序（按分组遍历顺序累加），便于前端/快照对账。
        Map<String, List<GroupPricing>> byModel = new LinkedHashMap<>();
        for (ModelGroup g : modelGroupRepository.findAll()) {
            // 仅启用 + 公开分组进入公开价格页对比（内部分层定价不外泄）。
            if (!g.status().isEnabled() || g.accessPolicy() != AccessPolicy.PUBLIC) {
                continue;
            }
            GroupPricing pricing = new GroupPricing(g.name(), g.code(), g.basePriceRatio().value());
            for (String modelName : g.models().values()) {
                byModel.computeIfAbsent(modelName, k -> new ArrayList<>()).add(pricing);
            }
        }
        return byModel;
    }
}
