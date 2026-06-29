package com.nexa.domain.growth.repository;

import com.nexa.domain.growth.model.Checkin;
import com.nexa.domain.growth.vo.CheckinDate;

import java.util.List;
import java.util.Optional;

/**
 * 签到记录仓储接口（领域层定义，基础设施层实现，DDD 依赖倒置 backend-engineer §2.3）。
 *
 * <p>domain 只声明「需要什么持久化能力」，不关心 JPA/SQL。应用层用例仅依赖本接口，可在单测中桩替换，
 * 无需起 DB。实现见 {@code infrastructure.persistence.CheckinRepositoryImpl}。</p>
 */
public interface CheckinRepository {

    /**
     * 查询某用户某日是否已有签到记录（GR-1 前置「当日 (user_id, checkin_date) 无记录」判定）。
     *
     * @param userId 用户 id
     * @param date   签到日期
     * @return 已存在返回 {@code true}
     */
    boolean existsByUserIdAndDate(long userId, CheckinDate date);

    /**
     * 持久化签到记录（GR-1 §4「写 Checkin 记录」）。
     *
     * <p>实现须保证：命中复合唯一索引 {@code idx_user_checkin_date}（同一用户同日并发重复签到）时，
     * 把底层唯一约束冲突转换为领域可识别的「已签到」信号（应用层据此抛
     * {@link com.nexa.domain.growth.exception.AlreadyCheckedInException}），而非吞错或泄露 SQL 异常。</p>
     *
     * @param checkin 待保存的签到记录聚合
     * @return 持久化后的签到记录（含数据库生成 id）
     */
    Checkin save(Checkin checkin);

    /**
     * 统计某用户累计签到次数（GR-2 {@code total_checkins}，全部历史记录数）。
     *
     * @param userId 用户 id
     * @return 历史签到总次数
     */
    long countByUserId(long userId);

    /**
     * 统计某用户累计领取额度（GR-2 {@code total_quota}，全部历史 quota_awarded 之和）。
     *
     * @param userId 用户 id
     * @return 历史累计领取额度（无记录返回 0）
     */
    long sumQuotaByUserId(long userId);

    /**
     * 查询某用户某日期范围内的签到记录（GR-2 本月日历，按 {@code checkin_date DESC}）。
     *
     * <p>对齐 prd-growth GR-2 §5 {@code GetUserCheckinRecords(userId, startDate, endDate)}，
     * 日期闭区间。用于本月日历热力 + 本月已签数统计。</p>
     *
     * @param userId    用户 id
     * @param startDate 起始日期（含）
     * @param endDate   截止日期（含）
     * @return 范围内签到记录（按日期降序）
     */
    List<Checkin> findByUserIdAndDateRange(long userId, CheckinDate startDate, CheckinDate endDate);

    /**
     * 查询某用户某日的签到记录（用于「今日是否已签」精确判定，可选）。
     *
     * @param userId 用户 id
     * @param date   日期
     * @return 命中记录（可空）
     */
    Optional<Checkin> findByUserIdAndDate(long userId, CheckinDate date);
}
