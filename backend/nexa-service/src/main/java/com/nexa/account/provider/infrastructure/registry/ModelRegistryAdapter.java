package com.nexa.account.provider.infrastructure.registry;

import com.nexa.account.provider.application.port.ModelRegistryPort;
import com.nexa.model.application.port.RefreshPricingPort;
import com.nexa.model.domain.model.ModelMeta;
import com.nexa.model.domain.repository.ModelMetaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模型登记端口的实现（账号域基础设施层适配器，「探测即自动入库」方案 1）。
 *
 * <p>跨 bounded context 写集成：账号域经 {@link ModelRegistryPort} 声明所需写能力，本 adapter
 * 用 {@code com.nexa.model} 的领域仓储 {@link ModelMetaRepository} 把探测到的模型名登记成
 * 平台模型元数据（ModelMeta）。这是 account→model 的允许集成点，与 model→channel 的只读
 * {@code ChannelModelCatalogAdapter} 互为镜像（context 间防腐，backend-engineer §2.5）。</p>
 *
 * <p><b>幂等</b>：按 model_name 幂等键查重（{@link ModelMetaRepository#findByModelName}），
 * 已存在的跳过，仅新建缺失的——重复探测同一批模型不会抛重名、不会产生重复记录。新建采用
 * 「本地自建」语义（{@link ModelMeta#create} → sync_official=0），不与官方同步覆盖打架：
 * 后续「上游模型同步」执行覆盖时，本地自建模型计入 skipped 不被改写（保留账号探测来源标记）。</p>
 *
 * <p><b>副作用</b>：有新建时触发 {@link RefreshPricingPort#refresh()}（与 {@code CreateModelMetaUseCase}
 * 创建后刷新定价缓存一致，PRD ML-1）。无新建时不触发，避免无谓刷新。</p>
 */
@Component
public class ModelRegistryAdapter implements ModelRegistryPort {

    private final ModelMetaRepository modelRepository;
    private final RefreshPricingPort refreshPricing;

    /**
     * @param modelRepository 模型元数据仓储（跨上下文写集成）
     * @param refreshPricing  定价刷新端口（新建后触发，与 model 域创建用例同源副作用）
     */
    public ModelRegistryAdapter(ModelMetaRepository modelRepository, RefreshPricingPort refreshPricing) {
        this.modelRepository = modelRepository;
        this.refreshPricing = refreshPricing;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public int registerModelsIfAbsent(List<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) {
            return 0;
        }
        // 去空白去空 + 去重（保序），防御性归一（端口契约要求调用方归一，这里再兜一道）。
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : modelNames) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }

        int created = 0;
        for (String name : normalized) {
            // 幂等：已存在（按 model_name）跳过，只新建缺失的。
            if (modelRepository.findByModelName(name).isPresent()) {
                continue;
            }
            // 本地自建语义（sync_official=0），仅带模型名，其余元数据留空待超管完善。
            ModelMeta model = ModelMeta.create(name, null, null, null, null, null, null);
            modelRepository.save(model);
            created++;
        }

        // 副作用：有新建才刷新定价缓存（与 CreateModelMetaUseCase 一致，PRD ML-1）。
        if (created > 0) {
            refreshPricing.refresh();
        }
        return created;
    }
}
