package com.nexa.application.growth;

import com.nexa.domain.growth.exception.CheckinDisabledException;
import com.nexa.domain.growth.model.Checkin;
import com.nexa.domain.growth.repository.CheckinRepository;
import com.nexa.domain.growth.repository.CheckinSettingRepository;
import com.nexa.domain.growth.vo.CheckinDate;
import com.nexa.domain.growth.vo.CheckinStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 签到状态与本月统计查询用例（PRD GR-2 «签到记录与累计统计查询»，F-1047）。
 *
 * <p>数据获取流（无控制分支）：校验启用 → 查累计统计（全量）+ 本月范围脱敏记录 → 组装
 * {@link CheckinStats}。脱敏铁律由读模型 {@link CheckinStats.Record} 从源头保证（只含
 * {@code checkin_date}/{@code quota_awarded}，不含 id/user_id，PRD GR-2 §5）。</p>
 *
 * <p>{@code @Transactional(readOnly = true)}：纯读，只读事务（无写、不入账）。</p>
 */
@Service
public class QueryCheckinStatusUseCase {

    /** {@code month} 参数格式 {@code YYYY-MM}（openapi {@code GET /api/user/checkin?month=}）。 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CheckinSettingRepository settingRepository;
    private final CheckinRepository checkinRepository;
    private final Clock clock;

    /**
     * @param settingRepository 签到配置仓储（读启用开关）
     * @param checkinRepository 签到记录仓储（累计统计 + 范围查询）
     * @param clock             时钟（注入便于单测固定「今日/本月」）
     */
    public QueryCheckinStatusUseCase(CheckinSettingRepository settingRepository,
                                     CheckinRepository checkinRepository,
                                     Clock clock) {
        this.settingRepository = settingRepository;
        this.checkinRepository = checkinRepository;
        this.clock = clock;
    }

    /**
     * 查询某用户的签到状态与指定月份统计。
     *
     * @param userId 用户 id（认证主体注入，self-scope）
     * @param month  月份 {@code YYYY-MM}，{@code null}/空白 = 本月（GR-2 §2「不带默认本月」）
     * @return 签到统计快照（累计 + 本月脱敏记录 + 今日是否已签）
     * @throws CheckinDisabledException 签到功能未启用（GR-2 C1-否，未启用空壳态）
     */
    @Transactional(readOnly = true)
    public CheckinStats query(long userId, String month) {
        if (!settingRepository.load().enabled()) {
            throw new CheckinDisabledException(); // GR-2 未启用空壳态
        }

        YearMonth target = resolveMonth(month);
        LocalDate first = target.atDay(1);
        LocalDate last = target.atEndOfMonth();

        // 累计统计（全量历史，total_quota/total_checkins，GR-2 §5）。
        long totalQuota = checkinRepository.sumQuotaByUserId(userId);
        long totalCheckins = checkinRepository.countByUserId(userId);

        // 本月范围记录（脱敏，checkin_date DESC），用于日历热力 + 本月已签数。
        List<Checkin> monthly = checkinRepository.findByUserIdAndDateRange(
                userId, CheckinDate.of(first), CheckinDate.of(last));
        List<CheckinStats.Record> records = monthly.stream()
                .map(c -> new CheckinStats.Record(c.checkinDate().toWire(), c.quotaAwarded()))
                .toList();

        // 今日是否已签：仅当查询月份包含今日时才有意义；否则按当月范围判定本月已签数即可。
        CheckinDate todayVo = CheckinDate.of(LocalDate.now(clock));
        boolean checkedInToday = checkinRepository.existsByUserIdAndDate(userId, todayVo);

        return CheckinStats.of(totalQuota, totalCheckins, records.size(), checkedInToday, records);
    }

    /**
     * 解析 {@code month} 参数为 {@link YearMonth}；空/非法回落本月（容错，不因坏参数报错破坏看板）。
     *
     * @param month {@code YYYY-MM} 或空
     * @return 目标年月（缺省本月）
     */
    private YearMonth resolveMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            // 坏的 month 参数不应让签到页 500：回落本月（GR-2 §2「不带默认本月」语义的宽松延伸）。
            return YearMonth.now(clock);
        }
    }
}
