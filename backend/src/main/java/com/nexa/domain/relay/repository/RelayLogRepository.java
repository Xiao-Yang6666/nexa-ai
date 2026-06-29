package com.nexa.domain.relay.repository;

import com.nexa.domain.relay.model.RelayLog;

/**
 * Relay 日志仓储接口（domain 定接口，infrastructure 实现，backend-engineer §2.3）。
 *
 * <p>职责：持久化一条 {@link RelayLog}（RL-7 第⑨步落 Log）。
 * 读接口在 billing BC 的 log 模块（查询用量/统计），本 relay BC 只关心写。</p>
 */
public interface RelayLogRepository {

    /**
     * 写入一条 relay 日志。
     *
     * @param log 领域日志对象
     */
    void save(RelayLog log);
}
