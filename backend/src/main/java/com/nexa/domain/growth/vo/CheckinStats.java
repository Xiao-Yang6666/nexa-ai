package com.nexa.domain.growth.vo;

import java.util.List;
import java.util.Objects;

/**
 * 签到累计统计值对象（PRD GR-2 «签到记录与累计统计查询»，F-1047）。
 *
 * <p>承载签到页所需的全部只读统计与本月脱敏记录（不可变快照）：
 * <ul>
 *   <li>{@code totalQuota}    累计领取额度（全部历史 quota_awarded 之和）</li>
 *   <li>{@code totalCheckins} 累计签到次数（全部历史记录数）</li>
 *   <li>{@code checkinCount}  本月已签数（当月范围记录数）</li>
 *   <li>{@code checkedInToday} 今日是否已签</li>
 *   <li>{@code records}       本月脱敏记录（仅 {@code checkin_date}/{@code quota_awarded}）</li>
 * </ul>
 * </p>
 *
 * <p><b>脱敏铁律</b>（PRD GR-2 §5「记录不含 id/user_id」）：内部记录用 {@link Record} 只携带
 * {@code checkinDate}/{@code quotaAwarded} 两项，从源头杜绝 id/user_id 进入读模型，接口层 DTO
 * 不可能误下发敏感字段（backend-engineer §3.4 客户视图 DTO 零敏感泄露）。</p>
 */
public final class CheckinStats {

    private final long totalQuota;
    private final long totalCheckins;
    private final int checkinCount;
    private final boolean checkedInToday;
    private final List<Record> records;

    private CheckinStats(long totalQuota, long totalCheckins, int checkinCount,
                         boolean checkedInToday, List<Record> records) {
        this.totalQuota = totalQuota;
        this.totalCheckins = totalCheckins;
        this.checkinCount = checkinCount;
        this.checkedInToday = checkedInToday;
        this.records = List.copyOf(records); // 防御性不可变拷贝，外部不可篡改读模型
    }

    /**
     * 组装签到统计快照。
     *
     * @param totalQuota     累计领取额度
     * @param totalCheckins  累计签到次数
     * @param checkinCount   本月已签数
     * @param checkedInToday 今日是否已签
     * @param records        本月脱敏记录（不含 id/user_id）
     * @return 签到统计值对象
     */
    public static CheckinStats of(long totalQuota, long totalCheckins, int checkinCount,
                                  boolean checkedInToday, List<Record> records) {
        Objects.requireNonNull(records, "records");
        return new CheckinStats(totalQuota, totalCheckins, checkinCount, checkedInToday, records);
    }

    /** @return 累计领取额度 */
    public long totalQuota() {
        return totalQuota;
    }

    /** @return 累计签到次数 */
    public long totalCheckins() {
        return totalCheckins;
    }

    /** @return 本月已签数 */
    public int checkinCount() {
        return checkinCount;
    }

    /** @return 今日是否已签 */
    public boolean checkedInToday() {
        return checkedInToday;
    }

    /** @return 本月脱敏记录（不可变，仅 checkin_date/quota_awarded） */
    public List<Record> records() {
        return records;
    }

    /**
     * 脱敏签到记录（PRD GR-2 §5「{@code CheckinRecord} 仅 checkin_date、quota_awarded」）。
     *
     * <p>读模型刻意<b>不含</b> id/user_id，是脱敏的第一道闸（领域层就不暴露敏感字段）。</p>
     *
     * @param checkinDate  签到日期 {@code YYYY-MM-DD}
     * @param quotaAwarded 当日领取额度
     */
    public record Record(String checkinDate, long quotaAwarded) {
    }
}
