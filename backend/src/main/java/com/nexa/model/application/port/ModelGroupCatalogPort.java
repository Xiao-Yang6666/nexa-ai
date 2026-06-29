package com.nexa.model.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 价格分组目录端口（model 域定接口，modelgroup BC 实现，公开价格页分组对比 ML-4）。
 *
 * <p>公开价格页（{@code GET /api/pricing}，匿名）需对每个上架模型展示「它在各可见价格分组里的价格对比」。
 * model 域只依赖本端口拿「模型名 → 可见分组及其倍率」，价格分组的查询/启用过滤/可见性判定等细节封装在
 * {@code com.nexa.modelgroup.infrastructure.pricing.ModelGroupCatalogAdapter}（依赖倒置，model 不编译期
 * 耦合 modelgroup 内部）。</p>
 *
 * <p><b>零泄露</b>：返回的 {@link GroupPricing} 只含分组展示名/编码/倍率（皆对客户公开），<b>绝不</b>含成本、
 * 利润、上游模型 B、渠道、供应商等内部口径。可见性按公开口径收窄：匿名公开价格页只暴露 PUBLIC 策略的启用
 * 分组，PRIVATE/AUTO_LEVEL 分组不进公开对比（避免泄露内部分层定价）。</p>
 */
public interface ModelGroupCatalogPort {

    /**
     * 批量查询「模型名 → 可见价格分组列表」（一次拉全量分组，广场一次性构建对比）。
     *
     * <p>仅返回<b>启用 + 公开（PUBLIC）</b>分组；每个模型对应其所属的可见分组（按分组的 models 集合反查）。
     * 模型不属于任何可见分组时，结果中无该键（调用方按空列表处理）。</p>
     *
     * @return 模型名（A，原样大小写）→ 该模型可见分组的定价列表
     */
    Map<String, List<GroupPricing>> visibleGroupsByModel();

    /**
     * 单个可见价格分组的定价投影（零泄露：仅展示名/编码/倍率）。
     *
     * @param name  分组展示名
     * @param code  分组编码
     * @param ratio 分组售价倍率（售价 = 模型基准倍率 × 本倍率）
     */
    record GroupPricing(String name, String code, BigDecimal ratio) {
    }
}
