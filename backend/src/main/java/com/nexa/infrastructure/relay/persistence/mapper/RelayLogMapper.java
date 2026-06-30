package com.nexa.infrastructure.relay.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.relay.persistence.po.LogPO;

/**
 * Relay 用量日志 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD。本 relay BC 只写日志（{@code insert} + 回填自增 id），
 * 读由 billing/log 模块负责，故此处无派生查询方法。仅供 {@link RelayLogRepositoryImpl} 内部使用，
 * 领域只认 {@code domain.repository.RelayLogRepository}。</p>
 */
public interface RelayLogMapper extends BaseMapper<LogPO> {
}
