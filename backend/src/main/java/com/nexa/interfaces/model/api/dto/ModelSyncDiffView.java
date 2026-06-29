package com.nexa.interfaces.model.api.dto;

import com.nexa.domain.model.vo.ModelSyncDiff;

import java.util.List;
import java.util.Map;

/**
 * 上游模型同步预览差异视图（接口层出参，F-3020）。对齐 openapi {@code ModelSyncDiff}（嵌套 diff 对象）。
 *
 * @param diff 差异明细（to_create_models / to_create_vendors / to_update_models / to_skip_models）
 */
public record ModelSyncDiffView(Map<String, List<String>> diff) {

    /**
     * 由领域值对象裁剪为视图（字段名对齐 openapi snake_case）。
     *
     * @param d 同步差异值对象
     * @return 差异视图
     */
    public static ModelSyncDiffView from(ModelSyncDiff d) {
        Map<String, List<String>> m = Map.of(
                "to_create_models", d.toCreateModels(),
                "to_create_vendors", d.toCreateVendors(),
                "to_update_models", d.toUpdateModels(),
                "to_skip_models", d.toSkipModels());
        return new ModelSyncDiffView(m);
    }
}
