package com.nexa.modelgroup.interfaces.api.dto;

import com.nexa.modelgroup.application.CreateModelGroupCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型组创建请求（管理端入参）。
 *
 * <p>{@code name}/{@code code}/{@code accessPolicy} 必填（缺失/非法由 domain 校验抛 400）。
 * 字段名经全局 Jackson SNAKE_CASE 反序列化（{@code base_price_ratio} → basePriceRatio 等）。</p>
 *
 * @param name           展示名（必填）
 * @param code           唯一编码（必填，[a-z0-9_-]）
 * @param basePriceRatio 基础倍率（可空→1.0）
 * @param models         可用模型名列表（可空）
 * @param accessPolicy   访问策略字面量（必填）
 * @param description    描述（可空）
 */
public record ModelGroupCreateRequest(String name, String code, BigDecimal basePriceRatio,
                                      List<String> models, String accessPolicy, String description) {

    /**
     * 转换为应用层创建命令。
     *
     * @return 创建命令
     */
    public CreateModelGroupCommand toCommand() {
        return new CreateModelGroupCommand(name, code, basePriceRatio, models, accessPolicy, description);
    }
}
