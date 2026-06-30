package com.nexa.interfaces.api.growth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.domain.growth.model.AffiliateAccount;

/**
 * 邀请统计客户视图（接口层 DTO，对齐 openapi UserSelfView 的 aff_* 三项，PRD GR-5 §5 / F-1045）。
 *
 * <p>展示邀请返利三项统计：邀请人数、当前可划转邀请额度、历史累计邀请额度。仅非敏感的本人增长数据，
 * 无成本/利润/上游信息（backend-engineer §3.4 客户视图零敏感泄露）。</p>
 *
 * @param affCount        累计邀请人数
 * @param affQuota        当前可划转邀请额度
 * @param affHistoryQuota 历史累计邀请额度
 */
public record AffiliateStatsVO(
        @JsonProperty("aff_count") long affCount,
        @JsonProperty("aff_quota") long affQuota,
        @JsonProperty("aff_history_quota") long affHistoryQuota) {

    /**
     * 由邀请返利账户聚合组装客户视图。
     *
     * @param account 邀请返利账户聚合
     * @return 客户视图
     */
    public static AffiliateStatsVO from(AffiliateAccount account) {
        return new AffiliateStatsVO(
                account.affCount(),
                account.affQuota(),
                account.affHistoryQuota());
    }
}
