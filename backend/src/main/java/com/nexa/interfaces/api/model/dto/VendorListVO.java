package com.nexa.interfaces.api.model.dto;

import java.util.List;

/**
 * 供应商列表视图（接口层出参，F-3018）。对齐 openapi {@code GET /api/vendors} 响应 data：items[] + total。
 *
 * @param items 供应商管理视图列表
 * @param total 总数
 */
public record VendorListVO(List<VendorAdminVO> items, long total) {
}
