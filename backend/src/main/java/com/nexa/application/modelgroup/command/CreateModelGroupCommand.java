package com.nexa.application.modelgroup.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建模型组命令（应用层入参 DTO，接口层 Request → 本命令 → 用例）。
 *
 * @param name           展示名（必填）
 * @param code           唯一编码（必填，[a-z0-9_-]）
 * @param basePriceRatio 基础倍率（可空→1.0）
 * @param models         可用模型名列表（可空）
 * @param accessPolicy   访问策略字面量（必填，PUBLIC/PRIVATE/AUTO_LEVEL）
 * @param description    描述（可空）
 */
public record CreateModelGroupCommand(String name, String code, BigDecimal basePriceRatio,
                                      List<String> models, String accessPolicy, String description) {
}
