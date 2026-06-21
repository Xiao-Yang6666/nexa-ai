package com.nexa.model.interfaces.api.dto;

import com.nexa.model.domain.model.ModelMeta;

import java.util.List;
import java.util.Map;

/**
 * 模型元数据列表视图（接口层出参，F-3013/F-3014）。
 *
 * <p>对齐 openapi {@code GET /api/models} 响应 data：{@code items[] + total + vendor_counts}。
 * 搜索（F-3014）复用本视图，vendor_counts 为空 map。</p>
 *
 * @param items        当前页模型管理视图
 * @param total        命中总数
 * @param vendorCounts 供应商计数（vendor_id 串 → 模型数；搜索时为空 map）
 */
public record ModelMetaListView(List<ModelMetaAdminView> items, long total, Map<String, Long> vendorCounts) {

    /**
     * 由应用层分页结果裁剪为列表视图。
     *
     * @param page 模型分页结果
     * @return 列表视图
     */
    public static ModelMetaListView from(com.nexa.model.application.ModelMetaPage page) {
        List<ModelMetaAdminView> views = page.items().stream().map(ModelMetaAdminView::from).toList();
        return new ModelMetaListView(views, page.total(), page.vendorCounts());
    }
}
