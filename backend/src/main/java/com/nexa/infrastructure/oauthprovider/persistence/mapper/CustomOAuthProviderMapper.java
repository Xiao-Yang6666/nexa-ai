package com.nexa.infrastructure.oauthprovider.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.oauthprovider.persistence.po.CustomOAuthProviderPO;

/**
 * 自定义 OAuth provider MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectOne/selectList/updateById/deleteById...）。
 * 仅供 {@link CustomOAuthProviderRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.CustomOAuthProviderRepository}。按 name 唯一索引的派生查询由 Impl 内
 * {@code LambdaQueryWrapper} 组装，不在此声明方法。</p>
 */
public interface CustomOAuthProviderMapper extends BaseMapper<CustomOAuthProviderPO> {
}
