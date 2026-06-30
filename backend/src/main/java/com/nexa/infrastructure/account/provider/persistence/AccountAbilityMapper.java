package com.nexa.infrastructure.account.provider.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.account.provider.persistence.po.AccountAbilityPO;

/**
 * Ability 路由索引 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD。按 group/models LIKE 粗筛、按 accountId fan-in 删除
 * 由 {@link AccountRepositoryImpl} 用 {@code LambdaQueryWrapper} 组装。</p>
 */
public interface AccountAbilityMapper extends BaseMapper<AccountAbilityPO> {
}
