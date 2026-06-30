package com.nexa.infrastructure.routing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.routing.persistence.po.AffinitySettingsPO;

/**
 * 亲和缓存全局策略 MyBatis-Plus Mapper（基础设施层内部接口，F-2031）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（selectById/insert/updateById...）。单例 id=1 行，
 * 由 {@link AffinityRuleRepositoryImpl} 的 {@code loadSettings/saveSettings} 走 {@code selectById(1)} +
 * merge（存在 updateById 否则 insert）读写。无自定义查询方法。</p>
 */
public interface AffinitySettingsMapper extends BaseMapper<AffinitySettingsPO> {
}
