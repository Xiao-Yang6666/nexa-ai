package com.nexa.channel.application;

import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.application.port.CodexUsageProbe;
import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.CodexUsage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Codex 渠道上游用量查询用例（应用层，F-4045）。
 *
 * <p>用例编排（API-ENDPOINTS §5.8）：
 * <ol>
 *   <li>按 id 加载渠道（缺失抛 {@link ChannelNotFoundException} → 404）。</li>
 *   <li>聚合护栏：{@code ensureCodex()}（非 Codex → 「channel type is not Codex」/400）、
 *       {@code ensureSingleKey()}（multi-key → 「multi-key channel is not supported」/400）。</li>
 *   <li>经端口 {@link ChannelProbeClient#queryCodexUsage} 查上游 wham 用量；端口内处理
 *       「上游 401/403 + 有 refresh_token → 自动刷新令牌重试」。access_token/account_id 缺失的
 *       凭证解析失败由领域 {@code codexCredential()} 抛 400（端口在解析时触发）。</li>
 *   <li><b>副作用收口（本用例事务边界内）</b>：若探测返回了刷新后的新 key
 *       （{@link CodexUsageProbe#keyRefreshed()}），则 {@code channel.refreshCodexKey} 回写并 save；
 *       且仅当 {@code channel.shouldReinitCacheAfterKeyRefresh()}（status∈{1,3}）时触发缓存重建
 *       （InitChannelCache，由 {@code cacheReinitializer} 端口承载——缓存属基础设施关注点）。</li>
 * </ol>
 * 用例薄、不含领域规则（规则在聚合充血方法上）；上游/凭证异常由 {@code ChannelExceptionHandler}
 * 统一翻译（400/404/502）。</p>
 *
 * <p>幂等键：channel_id（刷新回写幂等于最新 token——重复查询收敛到上游最新有效凭证）。</p>
 */
@Service
public class QueryCodexUsageUseCase {

    private final ChannelRepository channelRepository;
    private final ChannelProbeClient probeClient;
    private final ChannelCacheReinitializer cacheReinitializer;

    /**
     * @param channelRepository  渠道仓储
     * @param probeClient        上游探测端口
     * @param cacheReinitializer 渠道缓存重建端口（InitChannelCache）
     */
    public QueryCodexUsageUseCase(ChannelRepository channelRepository,
                                  ChannelProbeClient probeClient,
                                  ChannelCacheReinitializer cacheReinitializer) {
        this.channelRepository = channelRepository;
        this.probeClient = probeClient;
        this.cacheReinitializer = cacheReinitializer;
    }

    /**
     * 查询指定 Codex 渠道的上游用量（F-4045）。
     *
     * @param channelId 渠道 id
     * @return wham 用量结果（含是否触发了凭证刷新的标记）
     * @throws ChannelNotFoundException                                               渠道不存在
     * @throws com.nexa.channel.domain.exception.ChannelOperationNotSupportedException 非 Codex / 多 Key 渠道
     * @throws com.nexa.channel.domain.exception.InvalidChannelParameterException      凭证缺 access_token/account_id
     * @throws com.nexa.channel.domain.exception.ChannelUpstreamException              上游故障/刷新失败
     */
    @Transactional
    public CodexUsage query(long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));
        // 充血护栏：契约文案逐字对齐（非 Codex / 多 Key）。
        channel.ensureCodex();
        channel.ensureSingleKey();

        CodexUsageProbe probe = probeClient.queryCodexUsage(channel);

        // 副作用收口：上游 401/403 触发刷新并回传新 key 时，在本事务内回写渠道 key。
        if (probe.keyRefreshed()) {
            channel.refreshCodexKey(probe.refreshedKey());
            channelRepository.save(channel);
            // InitChannelCache：仅 status∈{1,3} 渠道需缓存与新凭证一致（手动禁用不参与路由，跳过）。
            if (channel.shouldReinitCacheAfterKeyRefresh()) {
                cacheReinitializer.reinitialize(channel.id());
            }
        }
        return probe.usage();
    }
}
