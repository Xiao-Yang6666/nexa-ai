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
 *   <li>第⑦步【记成本】quota_cost = CostRatio(实际渠道, B) × tokens（**不乘折扣**，客户不可见，随实际选中渠道走）；</li>
 *   <li>第⑨步【利润】quota_profit = quota_sell − quota_cost（可为负=亏损告警）；</li>
 *   <li>成本行缺失：quota_cost=0、quota_profit=quota_sell，Other 写 cost_missing（不阻断计费）。</li>
 * </ul>
 * </p>
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
     * @param basePriceRatio A 的基准售价倍率（PublicModel.BasePriceRatio / GetModelRatio(A)）
     * @param groupRatio     分组折扣系数（GetGroupRatio(UsingGroup)）
     * @param costRatio      (实际渠道, B) 的成本倍率（ChannelModelCost.CostRatio）；null=成本行缺失
     * @param completionRatio 出参 token 的成本/售价加权比（>1 表示 completion 更贵；1=不区分）
     * @return 双价记账结果（含 cost_missing 标记）
     */
    public static BillingResult compute(UsageIR usage,
                                        BigDecimal basePriceRatio,
                                        BigDecimal groupRatio,
                                        BigDecimal costRatio,
                                        BigDecimal completionRatio) {
        BigDecimal cr = completionRatio == null ? BigDecimal.ONE : completionRatio;
        // 加权 token = prompt + completion × completionRatio（completion 通常更贵）
        BigDecimal weightedTokens = BigDecimal.valueOf(usage.promptTokens())
                .add(BigDecimal.valueOf(usage.completionTokens()).multiply(cr));

        // ⑥ 售价：BasePriceRatio(A) × GroupRatio × weightedTokens（客户可见、恒定）
        BigDecimal sell = basePriceRatio.multiply(groupRatio).multiply(weightedTokens);
        int quotaSell = sell.setScale(0, RoundingMode.HALF_UP).intValue();

        // ⑦ 成本：CostRatio(渠道,B) × weightedTokens（不乘折扣）；缺失=0
        boolean costMissing = (costRatio == null);
        int quotaCost = 0;
        if (!costMissing) {
            BigDecimal cost = costRatio.multiply(weightedTokens);
            quotaCost = cost.setScale(0, RoundingMode.HALF_UP).intValue();
        }

        // ⑨ 利润 = 售价 − 成本（缺失成本时 profit=sell，可为负=亏损告警）
        int quotaProfit = quotaSell - quotaCost;

        return new BillingResult(quotaSell, quotaCost, quotaProfit, costMissing);
    }
}
