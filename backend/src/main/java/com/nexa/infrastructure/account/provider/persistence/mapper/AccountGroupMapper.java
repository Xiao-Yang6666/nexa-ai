package com.nexa.infrastructure.account.provider.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.account.provider.persistence.po.AccountGroupPO;

/**
 * 账号-分组关联 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD。fan-out/fan-in（按 accountId/group 增删查）由
 * {@link AccountRepositoryImpl} 用 {@code LambdaQueryWrapper} 组装。复合主键无自增列，
 * 批量插入用循环 {@code insert}（仓储侧），不依赖按复合主键 selectById。</p>
 */
public interface AccountGroupMapper extends BaseMapper<AccountGroupPO> {
}
