package com.nexa.interfaces.api.modelgroup.dto;

import com.nexa.domain.modelgroup.model.ModelGroup;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型组管理视图（管理端出参）。
 *
 * <p>AdminAuth 端点出参，回显模型组全部业务字段。倍率以 {@link BigDecimal} 序列化（精确小数）；
 * 字段名经全局 Jackson SNAKE_CASE 序列化为 {@code base_price_ratio/access_policy/created_time} 等。</p>
 *
 * @param id             主键
 * @param name           展示名
 * @param code           唯一编码
 * @param basePriceRatio 基础倍率
 * @param models         可用模型名列表
 * @param accessPolicy   访问策略字面量（PUBLIC/PRIVATE/AUTO_LEVEL）
 * @param status         状态整数码（1=启用 2=禁用）
 * @param description    描述（可空）
 * @param createdTime    创建时间（epoch 秒）
 * @param updatedTime    更新时间（epoch 秒）
 */
public record ModelGroupAdminVO(Long id, String name, String code, BigDecimal basePriceRatio,
                                  List<String> models, String accessPolicy, int status,
                                  String description, Long createdTime, Long updatedTime) {

    /**
     * 由领域聚合投影为管理视图。
     *
     * @param g 模型组聚合
     * @return 管理视图 DTO
     */
    public static ModelGroupAdminVO from(ModelGroup g) {
        return new ModelGroupAdminVO(
                g.id(),
                g.name(),
                g.code(),
                g.basePriceRatio().value(),
                g.models().values(),
                g.accessPolicy().wireValue(),
                g.status().code(),
                g.description(),
                g.createdTime(),
                g.updatedTime());
    }
}
