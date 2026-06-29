package com.nexa.application.model;

import com.nexa.application.model.port.RefreshPricingPort;
import com.nexa.application.model.port.UpstreamModelCatalog;
import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.model.Vendor;
import com.nexa.domain.model.repository.ModelMetaRepository;
import com.nexa.domain.model.repository.VendorRepository;
import com.nexa.domain.model.service.ModelSyncPlanner;
import com.nexa.domain.model.vo.ModelSyncDiff;
import com.nexa.domain.model.vo.ModelSyncResult;
import com.nexa.domain.model.vo.UpstreamModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 上游模型同步用例（应用层，F-3019/F-3020）。
 *
 * <p>用例编排：
 * <ul>
 *   <li>{@link #preview(String)} F-3020：预览仅返回差异不写库。</li>
 *   <li>{@link #execute(String, boolean, List)} F-3019：按勾选子集（可空→全量）落地新建/更新，
 *       跳过 sync_official=0 的本地自建模型，计入 skipped_models。</li>
 * </ul>
 * 核心比对逻辑委托领域服务 {@link ModelSyncPlanner}（跨聚合 + 可单测）。</p>
 */
@Service
public class SyncUpstreamModelsUseCase {

    private final UpstreamModelCatalog upstreamCatalog;
    private final ModelMetaRepository modelRepository;
    private final VendorRepository vendorRepository;
    private final ModelSyncPlanner planner;
    private final RefreshPricingPort refreshPricing;

    /**
     * @param upstreamCatalog  上游模型目录端口
     * @param modelRepository  模型仓储
     * @param vendorRepository 供应商仓储
     * @param planner          同步计划领域服务
     * @param refreshPricing   定价刷新端口
     */
    public SyncUpstreamModelsUseCase(UpstreamModelCatalog upstreamCatalog,
                                     ModelMetaRepository modelRepository,
                                     VendorRepository vendorRepository,
                                     ModelSyncPlanner planner,
                                     RefreshPricingPort refreshPricing) {
        this.upstreamCatalog = upstreamCatalog;
        this.modelRepository = modelRepository;
        this.vendorRepository = vendorRepository;
        this.planner = planner;
        this.refreshPricing = refreshPricing;
    }

    /**
     * 预览同步差异（F-3020，只读不写库）。
     *
     * @param locale 语言（用于上游 URL 选择，非法由端口回退默认）
     * @return 同步差异（toCreateModels/toCreateVendors/toUpdateModels/toSkipModels）
     */
    @Transactional(readOnly = true)
    public ModelSyncDiff preview(String locale) {
        List<UpstreamModel> upstream = upstreamCatalog.fetch(locale);
        List<ModelMeta> local = modelRepository.findAll();
        Set<String> existingVendors = vendorRepository.findAll().stream()
                .map(Vendor::name)
                .collect(Collectors.toSet());
        return planner.planDiff(upstream, local, existingVendors, true);
    }

    /**
     * 执行同步（F-3019，落地新建/更新/跳过）。
     *
     * @param locale    语言
     * @param overwrite 是否覆盖已有官方模型
     * @param models    勾选要同步的模型名子集（空/null → 全量）
     * @return 同步结果计数
     */
    @Transactional
    public ModelSyncResult execute(String locale, boolean overwrite, List<String> models) {
        List<UpstreamModel> upstream = upstreamCatalog.fetch(locale);
        if (upstream.isEmpty()) {
            // 无缺失且无 overwrite 时不请求上游直接返回零计数（BACKLOG T-121）。
            return ModelSyncResult.zero();
        }

        // 勾选子集过滤（非空且非全选时）。
        if (models != null && !models.isEmpty()) {
            Set<String> selected = Set.copyOf(models);
            upstream = upstream.stream()
                    .filter(u -> selected.contains(u.modelName()))
                    .toList();
        }
        if (upstream.isEmpty()) {
            return ModelSyncResult.zero();
        }

        // 预算差异。
        List<ModelMeta> localModels = modelRepository.findAll();
        Map<String, ModelMeta> localByName = localModels.stream()
                .collect(Collectors.toMap(ModelMeta::modelName, Function.identity(), (a, b) -> a));
        Set<String> existingVendors = vendorRepository.findAll().stream()
                .map(Vendor::name)
                .collect(Collectors.toSet());
        ModelSyncDiff diff = planner.planDiff(upstream, localModels, existingVendors, overwrite);

        int createdVendors = 0;
        int createdModels = 0;
        int updatedModels = 0;

        // 先创建缺失供应商（同步过程 upsert 供应商）。
        Map<String, Long> vendorNameToId = vendorRepository.findAll().stream()
                .collect(Collectors.toMap(v -> v.name().toLowerCase(), Vendor::id, (a, b) -> a));
        for (String vn : diff.toCreateVendors()) {
            Vendor v = Vendor.create(vn, null, null);
            v = vendorRepository.save(v);
            vendorNameToId.put(vn.toLowerCase(), v.id());
            createdVendors++;
        }

        // 创建缺失模型。
        Map<String, UpstreamModel> upstreamByName = upstream.stream()
                .collect(Collectors.toMap(UpstreamModel::modelName, Function.identity(), (a, b) -> a));
        for (String name : diff.toCreateModels()) {
            UpstreamModel up = upstreamByName.get(name);
            if (up == null) continue;
            Long vendorId = up.hasVendor() ? vendorNameToId.get(up.vendorName().toLowerCase()) : null;
            ModelMeta m = ModelMeta.fromUpstream(up, vendorId);
            modelRepository.save(m);
            createdModels++;
        }

        // 覆盖已有官方模型。
        for (String name : diff.toUpdateModels()) {
            ModelMeta m = localByName.get(name);
            if (m == null) continue;
            UpstreamModel up = upstreamByName.get(name);
            if (up == null) continue;
            Long vendorId = up.hasVendor() ? vendorNameToId.get(up.vendorName().toLowerCase()) : null;
            m.overwriteFromUpstream(up, vendorId);
            modelRepository.save(m);
            updatedModels++;
        }

        // 副作用：同步后刷新定价（PRD ML-1）。
        if (createdModels + updatedModels + createdVendors > 0) {
            refreshPricing.refresh();
        }

        return new ModelSyncResult(createdModels, createdVendors, updatedModels, diff.toSkipModels().size());
    }
}
