package com.nexa.infrastructure.routing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.routing.persistence.po.AffinityRulePO;

/**
 * 亲和规则 MyBatis-Plus Mapper（基础设施层内部接口，F-2031）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectOne/selectList/updateById/delete...）。仅供
 * {@link AffinityRuleRepositoryImpl} 内部使用，领域只认 {@code domain.repository.AffinityRuleRepository}。
 * 派生查询（按名查、启用过滤、稳定排序）及物理按名删（本表无软删）由 Impl 内 {@code LambdaQueryWrapper}
 * 组装走 {@code selectOne}/{@code selectList}/{@code delete}，无需自定义 {@code @Delete}。</p>
 */
public interface AffinityRuleMapper extends BaseMapper<AffinityRulePO> {
}
