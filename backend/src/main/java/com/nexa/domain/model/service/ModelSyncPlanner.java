package com.nexa.domain.model.service;

import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.vo.ModelSyncDiff;
import com.nexa.domain.model.vo.UpstreamModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型同步计划领域服务（纯领域逻辑，F-3019/F-3020）。
 *
 * <p>领域规则来源：PRD ML-2。本服务跨「上游模型条目集」与「本地 ModelMeta/Vendor 现状」两侧比对，
 * 计算同步差异（{@link #planDiff}）——是跨多个聚合的业务逻辑，按 DDD 放领域服务而非塞进某个聚合
 * （backend-engineer §2.4）。零框架依赖，可纯 JUnit 单测。</p>
 *
 * <p>比对规则：
 * <ul>
 *   <li>上游有、本地无 → 进 toCreateModels。</li>
 *   <li>上游有、本地有：
 *     <ul>
 *       <li>本地 sync_official=0（自建）→ 进 toSkipModels（绝不覆盖手工配置，PRD ML-2）。</li>
 *       <li>本地非自建 且 overwrite=true → 进 toUpdateModels。</li>
 *       <li>否则不动（既不更新也不计入 skip：无 overwrite 时本就不更新已有官方模型）。</li>
 *     </ul>
 *   </li>
 *   <li>上游供应商名本地无对应供应商 → 进 toCreateVendors（去重）。</li>
 * </ul>
 * </p>
 */
public class ModelSyncPlanner {

    /**
     * 计算同步差异（F-3020 预览 / F-3019 执行前置）。
     *
     * @param upstreamModels    上游模型条目（防腐层产出）
     * @param localModels       本地全部模型
     * @param existingVendorNames 本地已有供应商名集合（用于判断供应商是否需新建）
     * @param overwrite         是否覆盖已有官方模型
     * @return 同步差异
     */
    public ModelSyncDiff planDiff(List<UpstreamModel> upstreamModels,
                                  List<ModelMeta> localModels,
                                  Set<String> existingVendorNames,
                                  boolean overwrite) {
        // 本地模型名 → 聚合（用于 O(1) 命中判断与 sync_official 读取）。
        Map<String, ModelMeta> localByName = localModels.stream()
                .collect(Collectors.toMap(ModelMeta::modelName, Function.identity(), (a, b) -> a));

        List<String> toCreate = new ArrayList<>();
        List<String> toUpdate = new ArrayList<>();
        List<String> toSkip = new ArrayList<>();
        // 供应商去重：保序集合，避免同一上游供应商重复计入新建。
        Set<String> toCreateVendors = new LinkedHashSet<>();
        Set<String> localVendorLower = existingVendorNames == null ? Set.of()
                : existingVendorNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

        for (UpstreamModel up : upstreamModels) {
            if (!up.isValid()) {
                continue; // 上游脏数据（空模型名）跳过，不进任何清单。
            }
            String name = up.modelName();
            ModelMeta local = localByName.get(name);
            if (local == null) {
                toCreate.add(name);
            } else if (local.isLocalManaged()) {
                // 本地自建：绝不覆盖（PRD ML-2），无论 overwrite。
                toSkip.add(name);
            } else if (overwrite) {
                toUpdate.add(name);
            }
            // else：已有官方模型 + 无 overwrite → 不动。

            if (up.hasVendor() && !localVendorLower.contains(up.vendorName().toLowerCase())) {
                toCreateVendors.add(up.vendorName().trim());
            }
        }

        return new ModelSyncDiff(toCreate, new ArrayList<>(toCreateVendors), toUpdate, toSkip);
    }
}
