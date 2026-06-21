package com.nexa.model.interfaces.api.dto;

import java.util.List;

/**
 * 上游模型同步执行请求体（接口层入参，F-3019）。对齐 openapi {@code ModelSyncExecuteRequest}。
 *
 * @param locale    语言（可空）
 * @param overwrite 是否覆盖已有官方模型（可空 → false）
 * @param models    勾选要同步的模型名子集（可空/空 → 全量）
 */
public record ModelSyncExecuteRequest(String locale, Boolean overwrite, List<String> models) {
}
