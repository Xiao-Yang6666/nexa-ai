package com.nexa.infrastructure.growth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.growth.persistence.po.CheckinSettingPO;

/**
 * 签到配置 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD。单行配置表（固定主键
 * {@link CheckinSettingPO#SINGLETON_ID}），用 {@code selectById(1)} 读、固定主键 upsert（先 select
 * 判断再 insert/updateById）写，由 {@link CheckinSettingRepositoryImpl} 组装。无额外方法。</p>
 */
public interface CheckinSettingMapper extends BaseMapper<CheckinSettingPO> {
}
