package com.nexa.application.model;

import com.nexa.domain.model.model.ModelMeta;

import java.util.List;
import java.util.Map;

/**
 * 模型元数据分页结果（应用层输出，F-3013/F-3014）。
 *
 * <p>承载当前页模型聚合 + 总数 + 供应商计数（vendor_counts，F-3013 列表 enrich）。接口层据此
 * 裁剪成 AdminView 信封。vendorCounts 为「vendor_id 字符串 → 模型数」（vendor_id 为 null 的归到键 "0"）。</p>
 *
 * @param items        当前页模型聚合
 * @param total        命中总数
 * @param vendorCounts 供应商计数（搜索结果可为空 map）
 */
public record ModelMetaPage(List<ModelMeta> items, long total, Map<String, Long> vendorCounts) {
}
