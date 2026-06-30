package com.nexa.infrastructure.routing.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.routing.persistence.po.AffinityCachePO;

/**
 * 亲和缓存条目 MyBatis-Plus Mapper（基础设施层内部接口，F-2029/F-2032/F-2033）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectOne/updateById/delete...）。仅供
 * {@link AffinityCacheRepositoryImpl} 内部使用，领域只认 {@code domain.repository.AffinityCacheRepository}。
 * 三元组查询（rule_name + key_fingerprint + using_group 的 {@code IS NULL OR =} 兼容）及物理删除（清空 /
 * 按规则名删）由 Impl 内 {@code LambdaQueryWrapper} 组装走 {@code selectOne}/{@code delete}，本表无软删除，
 * 物理删返回受影响行数即领域所需删除条数，无需自定义 {@code @Delete}。</p>
 */
public interface AffinityCacheMapper extends BaseMapper<AffinityCachePO> {
}
