package com.nexa.infrastructure.relay.persistence;

import com.nexa.infrastructure.relay.persistence.po.LogPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Relay 日志 Spring Data JPA 仓库（基础设施层细节，F-3026~F-3037）。
 *
 * <p>由 {@link RelayLogRepositoryImpl} 适配为 domain 的
 * {@link com.nexa.domain.relay.repository.RelayLogRepository}。本 BC 只写日志，读由 billing/log 模块负责。</p>
 */
@Repository
public interface SpringDataLogRepository extends JpaRepository<LogPO, Long> {
}
