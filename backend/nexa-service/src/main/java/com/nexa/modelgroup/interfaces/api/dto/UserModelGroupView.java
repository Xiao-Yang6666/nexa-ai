package com.nexa.modelgroup.interfaces.api.dto;

import com.nexa.modelgroup.domain.model.ModelGroup;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户可选套餐分组视图（USER 端点出参，创建 apikey 选分组用）。
 *
 * <p>客户视图裁剪：只暴露选分组所需的最小信息——{@code code}（绑定键）、{@code name}（展示）、
 * {@code priceRatio}（分组倍率，前端展示 ×系数）、{@code models}（该套餐可用模型，供用户预览）。
 * <b>绝不</b>暴露成本/上游/授权明细。</p>
 *
 * @param code       分组业务编码（apikey 绑定键）
 * @param name       展示名
 * @param priceRatio 分组售价倍率（折扣=1 口径，前端展示 ×系数）
 * @param models     该套餐勾选的可用模型名列表（对外名 A）
 */
public record UserModelGroupView(
        String code,
        String name,
        BigDecimal priceRatio,
        List<String> models) {

    /**
     * 从模型组聚合投影为用户可选视图。
     *
     * @param g 可选用的模型组（已由 ResolveAccessibleModelGroupsUseCase 过滤 isSelectable）
     * @return 用户视图 DTO
     */
    public static UserModelGroupView from(ModelGroup g) {
        return new UserModelGroupView(
                g.code(),
                g.name(),
                g.basePriceRatio().value(),
                g.models().values());
    }
}
