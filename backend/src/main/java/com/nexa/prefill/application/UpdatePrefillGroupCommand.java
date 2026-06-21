package com.nexa.prefill.application;

import java.util.List;

/**
 * 更新预填分组命令（应用层入参 DTO，对齐 openapi {@code PrefillGroupUpdateRequest}）。
 *
 * <p>纯数据载体。{@code id} 必填（定位被更新分组）；{@code name}/{@code items} 可空——为 null 时
 * 表示该字段不变（部分更新语义，由聚合行为方法 {@code rename}/{@code replaceItems} 的 null 守卫
 * 处理）。type 不可更新（openapi 的 UpdateRequest 不含 type；改类型等价新建）。</p>
 *
 * @param id    被更新分组主键（必填）
 * @param name  新名称（null = 不改名）
 * @param items 新条目列表（null = 不改条目）
 */
public record UpdatePrefillGroupCommand(Long id, String name, List<String> items) {
}
