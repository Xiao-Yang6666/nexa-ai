package com.nexa.model.interfaces.api.dto;

import java.util.List;

/**
 * 供应商列表视图（接口层出参，F-3018）。对齐 openapi {@code GET /api/vendors} 响应 data：items[] + total。
 *
 * @param items 供应商管理视图列表
 * @param total 总数
 */
public record VendorListView(List<VendorAdminView> items, long total) {
}
