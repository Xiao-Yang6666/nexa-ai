package com.nexa.domain.log.vo;

import com.nexa.domain.log.exception.InvalidLogQueryException;

/**
 * 利润看板聚合维度值对象（F-6009 GET /api/profit/dashboard）。
 *
 * <p>对齐 openapi {@code dimension} 入参枚举 {@code [model, channel, group]}：决定 logs 按哪一列分组聚合
 * 售价/成本/利润。各维度的分组列在 {@link #columnName()} 给出（供原生 SQL 选择 GROUP BY 列）：</p>
 * <ul>
 *   <li>{@code MODEL}   → 按对外公开名 A（{@code resolved_public_model}）分组——可见性铁律：利润看板的模型维度
 *       按对外名 A 归集，绝不暴露上游模型 B/渠道明细；</li>
 *   <li>{@code CHANNEL} → 按渠道名（{@code channel_name}）分组——admin 端可见运营维度；</li>
 *   <li>{@code GROUP}   → 按用户分组（{@code group}）分组。</li>
 * </ul>
 *
 * <p>领域规则来源：prd-billing F-6009「利润分析看板（按维度聚合）」+ openapi ProfitDashboardItem
 * （{@code dimension_key}=维度键：model→A / channel→channel_name / group）。</p>
 */
public enum ProfitDimension {

    /** 按对外公开名 A 聚合（resolved_public_model）。 */
    MODEL("resolved_public_model"),
    /** 按渠道名聚合（channel_name）。 */
    CHANNEL("channel_name"),
    /** 按用户分组聚合（group）。 */
    GROUP("\"group\"");

    private final String columnName;

    ProfitDimension(String columnName) {
        this.columnName = columnName;
    }

    /**
     * 该维度对应 logs 表的分组列名（原生 SQL GROUP BY 用）。
     *
     * <p>注意：返回值来自固定枚举常量（非外部输入），不存在 SQL 注入风险——维度入参先经
     * {@link #fromWire(String)} 归一到枚举，未知值已被拒（400），到此处一定是受控列名。</p>
     *
     * @return 分组列名
     */
    public String columnName() {
        return columnName;
    }

    /**
     * 把 openapi 线上枚举字符串归一为维度枚举（缺省 MODEL）。
     *
     * @param wire 线上枚举值（{@code model/channel/group}，大小写不敏感；空/null 缺省 MODEL）
     * @return 对应维度枚举
     * @throws InvalidLogQueryException 当传入未知维度值（→400），不静默兜底以免聚合口径误解
     */
    public static ProfitDimension fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            // 缺省按模型维度（对外名 A）——与 openapi dimension 可选、最常用维度一致。
            return MODEL;
        }
        return switch (wire.trim().toLowerCase()) {
            case "model" -> MODEL;
            case "channel" -> CHANNEL;
            case "group" -> GROUP;
            default -> throw new InvalidLogQueryException(
                    "unknown profit dashboard dimension: " + wire);
        };
    }
}
