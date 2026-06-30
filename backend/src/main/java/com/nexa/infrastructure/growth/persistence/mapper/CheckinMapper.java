package com.nexa.infrastructure.growth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.growth.persistence.po.CheckinPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 签到记录 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD。派生查询（existsByUserIdAndCheckinDate、
 * findByUserIdAndCheckinDate、countByUserId、findRange）由 {@link CheckinRepositoryImpl} 用
 * {@code LambdaQueryWrapper} 组装；累计额度聚合查询无对应 wrapper 表达，声明显式 {@code @Select}。</p>
 */
public interface CheckinMapper extends BaseMapper<CheckinPO> {

    /**
     * 累计领取额度（GR-2 total_quota，全部 {@code quota_awarded} 之和）。
     *
     * <p>等价原 JPA {@code @Query SELECT COALESCE(SUM(c.quotaAwarded), 0) ... WHERE userId = :userId}。
     * {@code COALESCE(...,0)} 保证无记录时返回 0 而非 null。</p>
     *
     * @param userId 用户 id
     * @return 历史累计额度（无记录返回 0）
     */
    @Select("SELECT COALESCE(SUM(quota_awarded), 0) FROM checkins WHERE user_id = #{userId}")
    long sumQuotaByUserId(@Param("userId") Integer userId);
}
