package com.nexa.prefill.application;

import java.util.List;

/**
 * 创建预填分组命令（应用层入参 DTO，对齐 openapi {@code PrefillGroupCreateRequest}）。
 *
 * <p>纯数据载体（用例编排入参），不含领域逻辑——校验/规范化在 domain 聚合
 * {@code PrefillGroup.create} 与值对象内完成。{@code type} 为原始字面量，由用例经
 * {@code PrefillType.fromWire} 解析（非法 → 400）。</p>
 *
 * @param name        分组名称（必填，openapi required）
 * @param type        类型字面量（必填，model/tag/endpoint）
 * @param items       条目列表（可空，可含空白/重复，规范化在值对象）
 * @param description 描述（可空）
 */
public record CreatePrefillGroupCommand(String name, String type, List<String> items, String description) {
}
