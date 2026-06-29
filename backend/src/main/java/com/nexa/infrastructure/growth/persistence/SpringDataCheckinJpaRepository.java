package com.nexa.infrastructure.growth.persistence;

import com.nexa.infrastructure.growth.persistence.entity.CheckinJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（签到记录，基础设施层内部接口）。
 *
 * <p>仅供 {@link CheckinRepositoryImpl} 内部使用，不暴露给应用/领域层——领域只认
 * {@code domain.repository.CheckinRepository}。范围查询用 {@code checkin_date BETWEEN}（字符串
 * {@code YYYY-MM-DD} 字典序与日期序一致，可直接比较）。累计统计用聚合查询。</p>
 */
interface SpringDataCheckinJpaRepository extends JpaRepository<CheckinJpaEntity, Long> {

    /**
     * 判某用户某日是否已签（GR-1 查重）。
     *
     * @param userId      用户 id
     * @param checkinDate 日期 {@code YYYY-MM-DD}
     * @return 已存在返回 true
     */
    boolean existsByUserIdAndCheckinDate(Integer userId, String checkinDate);

    /**
     * 查某用户某日记录（今日是否已签精确判定）。
     *
     * @param userId      用户 id
     * @param checkinDate 日期
     * @return 命中实体（可空）
     */
    Optional<CheckinJpaEntity> findByUserIdAndCheckinDate(Integer userId, String checkinDate);

    /**
     * 累计签到次数（GR-2 total_checkins）。
     *
     * @param userId 用户 id
     * @return 历史记录总数
     */
    long countByUserId(Integer userId);

    /**
     * 累计领取额度（GR-2 total_quota，全部 quota_awarded 之和，无记录返回 null→上层归零）。
     *
     * @param userId 用户 id
     * @return 历史累计额度（可空）
     */
    @Query("SELECT COALESCE(SUM(c.quotaAwarded), 0) FROM CheckinJpaEntity c WHERE c.userId = :userId")
    long sumQuotaByUserId(@Param("userId") Integer userId);

    /**
     * 日期范围记录（GR-2 本月日历，{@code checkin_date} 闭区间，按日期降序）。
     *
     * <p>{@code checkin_date} 为定长 {@code YYYY-MM-DD} 字符串，字典序等同日期序，{@code BETWEEN}
     * 直接比较即可（PRD GR-2 §5 GetUserCheckinRecords + checkin_date DESC）。</p>
     *
     * @param userId    用户 id
     * @param startDate 起始日期 {@code YYYY-MM-DD}（含）
     * @param endDate   截止日期 {@code YYYY-MM-DD}（含）
     * @return 范围内记录（日期降序）
     */
    @Query("""
            SELECT c FROM CheckinJpaEntity c
            WHERE c.userId = :userId
              AND c.checkinDate >= :startDate
              AND c.checkinDate <= :endDate
            ORDER BY c.checkinDate DESC
            """)
    List<CheckinJpaEntity> findRange(@Param("userId") Integer userId,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);
}
