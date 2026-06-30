package com.nexa.infrastructure.account.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.account.persistence.po.UserOAuthBindingPO;

/**
 * 用户 OAuth 绑定 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectOne/selectList/deleteById...）。
 * 仅供 {@link OAuthBindingRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.OAuthBindingRepository}。派生查询（按 provider/userId 复合定位、列出用户全部绑定）
 * 由 Impl 内 {@code LambdaQueryWrapper} 组装，不在此声明方法。</p>
 */
public interface OAuthBindingMapper extends BaseMapper<UserOAuthBindingPO> {
}
