package com.nexa.infrastructure.account.provider.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.account.provider.persistence.po.AccountPO;

/**
 * 供应商账号 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD。平台过滤分页 / ACTIVE 初筛等查询由
 * {@link AccountRepositoryImpl} 用 {@code LambdaQueryWrapper} 组装。</p>
 */
public interface AccountMapper extends BaseMapper<AccountPO> {
}
