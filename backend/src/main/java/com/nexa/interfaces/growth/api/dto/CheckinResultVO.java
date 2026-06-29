package com.nexa.interfaces.growth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.growth.DailyCheckinUseCase;

/**
 * 签到结果客户视图（接口层 DTO，对齐 openapi {@code CheckinResult}）。
 *
 * <p>字段：{@code quota_awarded}（本次发放随机额度）+ {@code quota}（签到后可用余额——本切片不额外回查
 * 余额以省一次查询，按契约可选，置 0 由前端用 {@code quota_awarded} 增量提示；如需精确余额可在用例回查）。
 * 仅含非敏感增长数据，无成本/利润/上游信息（backend-engineer §3.4 客户视图零敏感泄露）。</p>
 *
 * @param quotaAwarded 本次签到发放的随机奖励额度
 * @param quota        签到后可用余额（本切片置 0，前端按增量展示；契约 quota 字段可选）
 */
public record CheckinResultVO(
        @JsonProperty("quota_awarded") long quotaAwarded,
        @JsonProperty("quota") long quota) {

    /**
     * 由用例结果组装视图。
     *
     * @param result 签到用例结果
     * @return 客户视图
     */
    public static CheckinResultVO from(DailyCheckinUseCase.CheckinResult result) {
        return new CheckinResultVO(result.quotaAwarded(), 0L);
    }
}
