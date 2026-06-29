package com.nexa.interfaces.growth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.domain.growth.vo.CheckinStats;

import java.util.List;

/**
 * 签到状态与本月统计客户视图（接口层 DTO，对齐 openapi {@code CheckinStatusVO}，PRD GR-2）。
 *
 * <p><b>脱敏铁律</b>（PRD GR-2 §5）：{@code records} 每条仅 {@code checkin_date}/{@code quota_awarded}，
 * <b>绝不含</b> id/user_id——脱敏在领域读模型 {@link CheckinStats.Record} 即已保证，本 DTO 据此组装，
 * 从结构上不可能误下发敏感字段（backend-engineer §3.4）。</p>
 *
 * @param totalQuota     累计领取额度
 * @param totalCheckins  累计签到次数
 * @param checkinCount   本月已签数
 * @param checkedInToday 今日是否已签
 * @param records        本月脱敏记录（仅 checkin_date/quota_awarded）
 */
public record CheckinStatusVO(
        @JsonProperty("total_quota") long totalQuota,
        @JsonProperty("total_checkins") long totalCheckins,
        @JsonProperty("checkin_count") int checkinCount,
        @JsonProperty("checked_in_today") boolean checkedInToday,
        @JsonProperty("records") List<RecordView> records) {

    /**
     * 由领域统计快照组装客户视图。
     *
     * @param stats 签到统计值对象
     * @return 客户视图
     */
    public static CheckinStatusVO from(CheckinStats stats) {
        List<RecordView> records = stats.records().stream()
                .map(r -> new RecordView(r.checkinDate(), r.quotaAwarded()))
                .toList();
        return new CheckinStatusVO(
                stats.totalQuota(),
                stats.totalCheckins(),
                stats.checkinCount(),
                stats.checkedInToday(),
                records);
    }

    /**
     * 脱敏签到记录视图（仅日期 + 额度，无 id/user_id）。
     *
     * @param checkinDate  签到日期 {@code YYYY-MM-DD}
     * @param quotaAwarded 当日领取额度
     */
    public record RecordView(
            @JsonProperty("checkin_date") String checkinDate,
            @JsonProperty("quota_awarded") long quotaAwarded) {
    }
}
