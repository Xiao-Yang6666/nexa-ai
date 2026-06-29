package com.nexa.application.modelgroup;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新模型组命令（应用层入参 DTO）。
 *
 * <p>部分更新语义：非 null 字段才覆盖（code 不可改，不在命令中）。
 *
 * @param id             模型组主键（必填）
 * @param name           新展示名（null=不改）
 * @param basePriceRatio 新基础倍率（null=不改）
 * @param models         新可用模型列表（null=不改）
 * @param accessPolicy   新访问策略字面量（null=不改）
 * @param description    新描述（null=不改）
 */
public record UpdateModelGroupCommand(long id, String name, BigDecimal basePriceRatio,
                                      List<String> models, String accessPolicy, String description) {
}
