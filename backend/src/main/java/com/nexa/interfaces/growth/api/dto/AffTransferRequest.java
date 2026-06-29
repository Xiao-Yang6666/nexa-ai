package com.nexa.interfaces.growth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 邀请额度划转请求（接口层 DTO，PRD GR-5 / F-1044，{@code POST /api/user/self/aff_transfer}）。
 *
 * <p>对齐 openapi 请求体 {@code {quota}}（必填，&gt;= QuotaPerUnit）。最小单位/余额校验由聚合
 * {@code AffiliateAccount.transferToQuota} 守护，本 DTO 仅承载传输。</p>
 *
 * @param quota 划转额度
 */
public record AffTransferRequest(
        @JsonProperty("quota") Long quota) {

    /**
     * @return 划转额度（null 视为 0，由聚合按「低于最小单位」拒绝）
     */
    public long quotaOrZero() {
        return quota == null ? 0L : quota;
    }
}
