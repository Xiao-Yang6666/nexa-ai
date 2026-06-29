package com.nexa.model.interfaces.api.dto;

import com.nexa.model.domain.model.PublicModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 公开模型价格页视图（接口层出参，<b>PublicView 零泄露</b>，F-2048 / ML-4）。
 *
 * <p>对齐 openapi {@code PricingPublicView}。本端点 {@code GET /api/pricing} 为公开端点
 * （{@code security: []}，未登录可访问），故本 DTO 是<b>客户/公开视图的最外层裁剪闸</b>：
 * 字段全部显式逐条声明（非反射拷贝整实体），从源头杜绝内部敏感信息泄露。</p>
 *
 * <p><b>客户端零泄露铁律（产品铁律，backend-engineer §3 / COMPAT-BILLING-DECISIONS §4）</b>：
 * 公开价格页<b>绝不</b>暴露以下字段——成本（cost/cost_ratio）、利润（profit）、上游模型 B、
 * 渠道（channel）、供应商（vendor/supplier）、任何内部计费成本口径。这些字段在领域聚合
 * {@link PublicModel} 与 {@code public_models} 表中<b>根本不存在</b>（售价/成本物理分离，
 * 成本挂渠道×B 另表），本投影只读取 {@link PublicModel} 的公开可见字段，二重保险。</p>
 *
 * <p>暴露字段（公开可见，对客户恒定）：
 * <ul>
 *   <li>{@code models[].model_name}        对外名 A（public_name，客户可见商品键）</li>
 *   <li>{@code models[].base_price_ratio}   基准售价倍率（折扣=1 口径，对客户恒定）</li>
 *   <li>{@code models[].display_name}       展示名</li>
 *   <li>{@code models[].description}        描述</li>
 *   <li>{@code models[].groups}             该模型所在的可见价格分组及倍率（分组价格对比，公开 PUBLIC 子集）</li>
 *   <li>{@code models[].supported_endpoint} 支持端点（ML-4 元信息；本商品目录无该列 → 空）</li>
 *   <li>{@code models[].cache_ratio}        缓存倍率（ML-4 元信息；本商品目录无该列 → 省略）</li>
 *   <li>{@code group_ratio}                 仅当前可用分组的折扣系数（匿名 → 公开分组 free=1.0）</li>
 *   <li>{@code auto_groups}                 自动分组列表（ML-4 元信息）</li>
 *   <li>{@code pricing_version}             价格版本常量（前端缓存/对账用）</li>
 * </ul>
 * </p>
 *
 * @param models         上架对外模型公开定价条目（已按 sort_order/id 升序）
 * @param groupRatio     可用分组 → 折扣系数（匿名公开分组子集，绝不含不可用分组）
 * @param autoGroups     自动分组列表
 * @param pricingVersion 价格版本常量
 */
public record PricingPublicView(
        List<Item> models,
        Map<String, BigDecimal> groupRatio,
        List<String> autoGroups,
        String pricingVersion
) {

    /**
     * 单条公开模型定价（PublicView 条目，逐字段公开投影）。
     *
     * @param modelName         对外名 A（public_name）
     * @param basePriceRatio    基准售价倍率（折扣=1 口径）
     * @param displayName       展示名
     * @param description       平台配置的描述
     * @param groups            该模型所在的可见价格分组及倍率（分组价格对比；无则空列表）
     * @param supportedEndpoint 支持端点（商品目录无此列 → null，按 non_null 省略）
     * @param cacheRatio        缓存倍率（商品目录无此列 → null，按 non_null 省略）
     */
    public record Item(
            String modelName,
            BigDecimal basePriceRatio,
            String displayName,
            String description,
            List<GroupPrice> groups,
            String supportedEndpoint,
            BigDecimal cacheRatio
    ) {
        /**
         * 由领域聚合 + 可见分组裁剪为公开定价条目（<b>零泄露投影</b>：只读公开字段，成本/利润/B/渠道根本不取）。
         *
         * @param m      上架对外模型聚合
         * @param groups 该模型所在的可见价格分组（公开 PUBLIC 子集，可空 → 空列表）
         * @return 公开定价条目
         */
        public static Item from(PublicModel m, List<GroupPrice> groups) {
            // 显式逐字段：仅 publicName/basePriceRatio/displayName/description 来自聚合（皆公开）。
            // supported_endpoint/cache_ratio 是 ML-4 契约元信息字段，public_models 表无对应列，
            // 故置 null（Jackson non_null 省略），既守契约 schema 形状又不杜撰内部数据。
            return new Item(
                    m.publicName(),
                    m.basePriceRatio(),
                    blankToNull(m.displayName()),
                    blankToNull(m.description()),
                    groups == null ? List.of() : groups,
                    null,
                    null);
        }

        /** 空白展示名归 null（non_null 序列化省略，避免输出无意义空串）。 */
        private static String blankToNull(String s) {
            return (s == null || s.isBlank()) ? null : s;
        }
    }

    /**
     * 单个价格分组的公开定价（分组价格对比条目，零泄露：仅展示名/编码/倍率）。
     *
     * @param name  分组展示名
     * @param code  分组编码
     * @param ratio 分组售价倍率（售价 = 模型 base_price_ratio × 本倍率）
     */
    public record GroupPrice(String name, String code, BigDecimal ratio) {
    }
}
