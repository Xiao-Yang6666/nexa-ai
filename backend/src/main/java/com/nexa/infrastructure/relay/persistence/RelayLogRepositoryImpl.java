package com.nexa.infrastructure.relay.persistence;

import com.nexa.infrastructure.relay.persistence.mapper.RelayLogMapper;

import com.nexa.domain.relay.model.RelayLog;
import com.nexa.domain.relay.repository.RelayLogRepository;
import com.nexa.infrastructure.relay.persistence.po.LogPO;
import org.springframework.stereotype.Repository;

/**
 * Relay 日志仓储 {@link RelayLogRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-3026~F-3037 落 Log）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link RelayLogMapper} + PO 就近工厂方法
 * （{@link LogPO#of}）实现。{@code channelId}/{@code userId}/{@code tokenId} 在领域为 Long
 * （与 account BC 主键一致），Log 表为 int 列（现网兼容），窄化在 {@link LogPO#of} 内完成
 * （值域受控，relay 日志不会超 int 范围）。本 BC 只写日志（save），读由 billing/log 模块负责。</p>
 */
@Repository
public class RelayLogRepositoryImpl implements RelayLogRepository {

    private final RelayLogMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public RelayLogRepositoryImpl(RelayLogMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public void save(RelayLog log) {
        LogPO po = LogPO.of(log);
        mapper.insert(po);              // 回填自增 id 到 po
        log.assignId(po.getId());
    }
}
