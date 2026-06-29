package com.nexa.relay.domain.service;

import com.nexa.relay.domain.ir.UsageIR;
import com.nexa.relay.domain.vo.BillingResult;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 双价记账领域服务（RL-7 第⑥⑦⑨步，纯函数零框架依赖）。
 *
 * <p>领域规则来源：COMPAT-BILLING-DECISIONS §10 + prd-relay RL-7 + BILLING-DATA-OBJECTS。核心铁律：
 * <ul>
 *   <li>第⑥步【扣客户】quota_sell = BasePriceRatio(A) × GroupRatio(分组) × tokens（客户可见，**售价只随对外模型 A + 分组折扣变化，兜底切供应商时恒定不波动**）；</li>
 *   <li>第⑦步【记成本】quota_cost = BasePriceRatio(A) × AccountRatio(所用账号) × tokens（**不乘折扣、不进售价**，客户不可见，随实际选中账号走）；</li>
 *   <li>第⑨步【利润】quota_profit = quota_sell − quota_cost（可为负=亏损告警）。</li>
 * </ul>
 * </p>
 *
 * <p>成本口径（2026-06 重设计）：成本不再按"渠道×上游模型 B"二维配置，而是<b>每账号一个倍率</b>
 * （{@code Account.rateMultiplier}）。成本 = A 基准价倍率 × 账号倍率 × tokens，即"该账号成本是售价基准的几倍"。
 * 账号倍率只乘在<b>成本侧</b>、不进售价，故不破坏"售价随兜底切供应商恒定不波动"铁律。账号倍率 null → 1.0。</p>
 *
 * <p>金额用 {@link BigDecimal} 计算（禁裸 float，backend-engineer §2.4 / Pitfall 7），最终配额取整为 int
 * （Log.quota_sell/cost/profit 为 integer 口径，与现网 Quota 一致）。token 用量取 IR 统一口径
 * （prompt + completion，D5）。</p>
 */
public final class DualPriceBilling {

    private DualPriceBilling() {
    }

    /**
     * 计算一笔请求的双价记账（售价/成本/利润）。
     *
     * @param usage          IR token 用量（D5 统一口径）
     * @param basePriceRatio A 的基准售价倍率（PublicModel.BasePriceRatio / GetModelRatio(A)）；同时作为成本基准
     * @param groupRatio     分组折扣系数（GetGroupRatio(UsingGroup)），仅作用于售价
     * @param userDiscount   用户专属折扣（User.discountRatio），在分组折扣之后再乘，仅作用于售价；null=1.0
     * @param accountRatio   所用账号的成本倍率（Account.rateMultiplier）；null=1.0（未选中账号/回落）
     * @param completionRatio 出参 token 的成本/售价加权比（>1 表示 completion 更贵；1=不区分）
     * @return 双价记账结果（账号倍率口径下成本恒可算，costMissing 恒 false）
     */
    public static BillingResult compute(UsageIR usage,
                                        BigDecimal basePriceRatio,
                                        BigDecimal groupRatio,
                                        BigDecimal userDiscount,
                                        BigDecimal accountRatio,
                                        BigDecimal completionRatio) {
        BigDecimal cr = completionRatio == null ? BigDecimal.ONE : completionRatio;
        BigDecimal ar = accountRatio == null ? BigDecimal.ONE : accountRatio;
        BigDecimal ud = userDiscount == null ? BigDecimal.ONE : userDiscount;
        // 加权 token = prompt + completion × completionRatio（completion 通常更贵）
        BigDecimal weightedTokens = BigDecimal.valueOf(usage.promptTokens())
                .add(BigDecimal.valueOf(usage.completionTokens()).multiply(cr));

        // ⑥ 售价：BasePriceRatio(A) × GroupRatio × UserDiscount × weightedTokens
        //   （客户可见、恒定，分组折扣后再乘用户专属折扣；不含 account 倍率）
        BigDecimal sell = basePriceRatio.multiply(groupRatio).multiply(ud).multiply(weightedTokens);
        int quotaSell = sell.setScale(0, RoundingMode.HALF_UP).intValue();

        // ⑦ 成本：BasePriceRatio(A) × AccountRatio(账号) × weightedTokens（不乘折扣、不进售价）
        BigDecimal cost = basePriceRatio.multiply(ar).multiply(weightedTokens);
        int quotaCost = cost.setScale(0, RoundingMode.HALF_UP).intValue();

        // ⑨ 利润 = 售价 − 成本（可为负=亏损告警）
        int quotaProfit = quotaSell - quotaCost;

        // 账号倍率口径下成本恒可算（账号必选中），不再有"成本行缺失"态。
        return new BillingResult(quotaSell, quotaCost, quotaProfit, false);
    }
}
