package com.nexa.domain.relay.port;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 模型组定价端口（domain 定接口，infrastructure 由 modelgroup BC 实现，REQ-05 计费）。
 *
 * <p>灵活模型组管理的计费接入点：售价倍率不再写死在用户等级上，而是取调用方<b>选中模型组</b>的
 * 模型组级倍率（{@code ModelGroup.basePriceRatio}）。relay 域只依赖本端口拿「某分组 code 对应的售价
 * 倍率」，模型组的查询/校验/软删过滤等细节封装在
 * {@code com.nexa.infrastructure.modelgroup.pricing.ModelGroupPricingAdapter}（依赖倒置，
 * backend-engineer §2.3——relay 不编译期耦合 modelgroup 内部）。</p>
 *
 * <p>计费语义：售价 {@code quota_sell = BasePriceRatio(A) × 模型组倍率 × tokens}（模型组倍率替代原
 * GroupRatio 折扣位）。无对应模型组（分组 code 未配置 / 已禁用 / 已软删）时返回
 * {@link Optional#empty()}，由调用方回落兜底倍率 {@code 1.0}（保持旧行为，不阻断计费）。</p>
 */
public interface ModelGroupPricingPort {

    /**
     * 取指定分组 code 对应模型组的售价倍率。
     *
     * @param groupCode 调用方使用的分组 code（来自 {@code Token.group}）
     * @return 命中且可用（启用）的模型组售价倍率；无匹配/禁用返回 {@link Optional#empty()}
     */
    Optional<BigDecimal> priceRatioOf(String groupCode);
}
