package com.nexa.domain.model.vo;

import java.util.List;

/**
 * 上游模型同步预览差异值对象（F-3020，只读差异，不写库）。
 *
 * <p>领域规则来源：PRD ML-2。预览仅比对上游 basellm 元数据与本地现状，给出将创建/更新/跳过的
 * 模型与将创建的供应商清单，供管理员勾选后再执行同步（F-3019），本身绝不落库。</p>
 *
 * <p>不可变值对象（构造即定型），各列表防御性复制为不可变 List。</p>
 *
 * @param toCreateModels  将创建的模型名（本地缺失）
 * @param toCreateVendors 将创建的供应商名（本地缺失）
 * @param toUpdateModels  将更新的模型名（本地已有且开启 overwrite）
 * @param toSkipModels    将跳过的模型名（sync_official=0 的本地自建，不覆盖）
 */
public record ModelSyncDiff(List<String> toCreateModels,
                            List<String> toCreateVendors,
                            List<String> toUpdateModels,
                            List<String> toSkipModels) {

    /** 规范构造：各列表防御性复制为不可变（null → 空）。 */
    public ModelSyncDiff {
        toCreateModels = toCreateModels == null ? List.of() : List.copyOf(toCreateModels);
        toCreateVendors = toCreateVendors == null ? List.of() : List.copyOf(toCreateVendors);
        toUpdateModels = toUpdateModels == null ? List.of() : List.copyOf(toUpdateModels);
        toSkipModels = toSkipModels == null ? List.of() : List.copyOf(toSkipModels);
    }

    /** @return 空差异（上游无新增/变更时） */
    public static ModelSyncDiff empty() {
        return new ModelSyncDiff(List.of(), List.of(), List.of(), List.of());
    }
}
