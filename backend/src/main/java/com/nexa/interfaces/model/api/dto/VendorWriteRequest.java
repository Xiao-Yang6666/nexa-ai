package com.nexa.interfaces.model.api.dto;

/**
 * 供应商写请求体（创建/更新共用，接口层入参，F-3018）。对齐 openapi {@code VendorWriteRequest}。
 *
 * @param id     供应商 id（更新时必填，创建时忽略）
 * @param name   供应商名（必填）
 * @param icon   图标
 * @param status 状态码
 */
public record VendorWriteRequest(Long id, String name, String icon, Integer status) {
}
