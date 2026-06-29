package com.nexa.infrastructure.relay.persistence;

import com.nexa.domain.relay.model.RelayLog;
import com.nexa.domain.relay.repository.RelayLogRepository;
import com.nexa.infrastructure.relay.persistence.entity.LogJpaEntity;
import org.springframework.stereotype.Repository;

/**
 * Relay 日志仓储 JPA 实现（基础设施层适配器，F-3026~F-3037 落 Log）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link SpringDataLogRepository} + 实体映射实现。
 * {@code channelId}/{@code userId}/{@code tokenId} 在领域为 Long（与 account/channel BC 主键一致），
 * Log 表为 int 列（现网兼容），映射时窄化（值域受控，relay 日志不会超 int 范围）。</p>
 */
@Repository
public class RelayLogRepositoryImpl implements RelayLogRepository {

    private final SpringDataLogRepository jpa;

    public RelayLogRepositoryImpl(SpringDataLogRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(RelayLog log) {
        LogJpaEntity e = new LogJpaEntity();
        e.setUserId(toInt(log.userId()));
        e.setCreatedAt(log.createdAt());
        e.setType(log.type() == null ? 0 : log.type().code());
        e.setContent(log.content());
        e.setUsername(log.username());
        e.setTokenName(log.tokenName());
        e.setModelName(log.modelName());
        e.setQuota(log.quota());
        e.setPromptTokens(log.promptTokens());
        e.setCompletionTokens(log.completionTokens());
        e.setUseTime(log.useTime());
        e.setIsStream(log.isStream());
        e.setChannelId(toInt(log.channelId()));
        e.setTokenId(toInt(log.tokenId()));
        e.setGroup(log.group());
        e.setIp(log.ip());
        e.setRequestId(log.requestId());
        e.setUpstreamRequestId(log.upstreamRequestId());
        e.setOther(log.other());
        e.setRequestedModel(log.requestedModel());
        e.setResolvedPublicModel(log.resolvedPublicModel());
        e.setActualUpstreamModel(log.actualUpstreamModel());
        e.setInboundProtocol(log.inboundProtocol());
        e.setUpstreamProtocol(log.upstreamProtocol());
        e.setProtocolConverted(log.isProtocolConverted());
        e.setUserAgent(log.userAgent());
        e.setQuotaSell(log.quotaSell());
        e.setQuotaCost(log.quotaCost());
        e.setQuotaProfit(log.quotaProfit());
        LogJpaEntity saved = jpa.save(e);
        log.assignId(saved.getId());
    }

    private Integer toInt(Long v) {
        return v == null ? null : v.intValue();
    }
}
