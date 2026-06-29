package com.nexa.interfaces.model.api.dto;

/**
 * 上游模型同步预览请求体（接口层入参，F-3020）。对齐 openapi {@code ModelSyncPreviewRequest}。
 *
 * @param locale 语言（en/zh-CN/zh-TW/ja，非法由端口回退默认 URL）
 */
public record ModelSyncPreviewRequest(String locale) {
}
