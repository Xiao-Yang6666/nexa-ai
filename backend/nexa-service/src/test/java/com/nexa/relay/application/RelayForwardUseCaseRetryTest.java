package com.nexa.relay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelModelCostRepository;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.relay.domain.exception.NoAvailableChannelException;
import com.nexa.relay.domain.model.RelayLog;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.port.UpstreamResponse;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.vo.LogType;
import com.nexa.routing.application.SelectRelayChannelUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RelayForwardUseCase RL-3 重试/禁用/错误处置单元测试（REQ-09）。
 *
 * <p>聚焦转发主干的重试循环行为：可重试码切渠重试、不可重试码直接返、重试耗尽返最后错误、
 * AutoBan 命中禁用渠道、错误 Log Type=5、计费仅在最终成功渠道结算。其余链路（映射/选渠算法/
 * 计费纯函数）由各自单测覆盖，本测以 mock 桩替换跨 BC 端口。</p>
 */
class RelayForwardUseCaseRetryTest {

    private static final String PATH = "/v1/chat/completions";
    private static final byte[] BODY = "{\"model\":\"gpt-4o\"}".getBytes();

    private UpstreamHttpPort upstreamHttpPort;
    private ChannelRepository channelRepo;
    private RelayLogRepository logRepo;
    private SelectRelayChannelUseCase selectUseCase;
    private UserQuotaAccount userQuotaAccount;
    private RelayForwardUseCase useCase;

    private RelayAuthContext auth;

    @BeforeEach
    void setUp() {
        UserModelAliasRepository l1Repo = mock(UserModelAliasRepository.class);
        logRepo = mock(RelayLogRepository.class);
        upstreamHttpPort = mock(UpstreamHttpPort.class);
        channelRepo = mock(ChannelRepository.class);
        KeyLimitGuard keyLimitGuard = mock(KeyLimitGuard.class);
        selectUseCase = mock(SelectRelayChannelUseCase.class);
        PublicModelRepository publicModelRepo = mock(PublicModelRepository.class);
        ChannelModelCostRepository costRepo = mock(ChannelModelCostRepository.class);
        userQuotaAccount = mock(UserQuotaAccount.class);

        // C→A 映射：无 L1 命中 → C 原样透传为 A（A→B 由渠道级 modelMapping 解析，本测渠道未配映射→B=A）。
        when(l1Repo.findTargetByAlias(any(), anyString())).thenReturn(Optional.empty());
        when(publicModelRepo.findByPublicName(anyString())).thenReturn(Optional.empty());
        when(costRepo.findByChannelAndUpstream(org.mockito.ArgumentMatchers.anyInt(), anyString()))
                .thenReturn(Optional.empty());

        useCase = new RelayForwardUseCase(l1Repo, logRepo, upstreamHttpPort, channelRepo,
                new ObjectMapper(), keyLimitGuard, selectUseCase, publicModelRepo, costRepo, userQuotaAccount,
                // 模型组定价端口：返回 empty → 售价倍率回落 1.0（保持本测试原有计费口径不变）。
                groupCode -> java.util.Optional.empty(),
                // 模型组访问端口：恒放行 → 不拦截（保持本测试原有放行行为）。
                (groupCode, userId, tokenId) -> true,
                // 账号选择端口：选不到账号 → 转发回落 channel 自带 key/baseUrl（保持本测试原有转发口径不变）。
                new com.nexa.relay.domain.port.AccountSelectionPort() {
                    @Override public java.util.Optional<com.nexa.relay.domain.port.SelectedAccount> selectAccount(
                            String group, String platform, java.util.Set<Long> excludeAccountIds) {
                        return java.util.Optional.empty();
                    }
                    @Override public void markRateLimited(long accountId, Long resetAt) { }
                    @Override public void markOverloaded(long accountId, Long until) { }
                });
        // tokenId=null → 跳过 KeyLimitGuard 校验（鉴权未接线占位）。
        auth = new RelayAuthContext(7L, "alice", "default", null, null);
    }

    private Channel channel(long id, int autoBan) {
        return Channel.rehydrate(id, 1, "sk-key" + id, ChannelStatus.ENABLED.code(), "ch" + id,
                0, "https://up" + id, "gpt-4o", "default", 0L, autoBan,
                java.math.BigDecimal.ZERO, 0L, null, null, null, "", null, null, null, 0L);
    }

    @Test
    void retryableErrorSwitchesChannelThenSucceeds() {
        Channel c1 = channel(1L, 1);
        Channel c2 = channel(2L, 1);
        // 首次选渠 c1（503 可重试）→ 排除 c1 后选 c2（200 成功）。
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of()))).thenReturn(c1);
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of(1L)))).thenReturn(c2);

        UpstreamResponse err503 = UpstreamResponse.of(503, Map.of(), "{\"error\":{\"message\":\"busy\"}}".getBytes());
        UpstreamResponse ok = UpstreamResponse.of(200, Map.of(),
                "{\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5}}".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err503, ok);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        assertEquals(200, result.statusCode());
        // 调上游 2 次（c1 失败 + c2 成功）。
        verify(upstreamHttpPort, times(2)).send(any());
        // 计费结算只在最终成功渠道发生一次（c2）。
        verify(userQuotaAccount, times(1)).debit(anyLong(), any());
        // 503 不在 401/403，autoBan 不该触发禁用 → channelRepo.save 不被调用。
        verify(channelRepo, never()).save(any());
        // 一条 Type=5 错误 Log（c1 失败）+ 一条 Type=2 消费 Log（c2 成功）。
        ArgumentCaptor<RelayLog> logs = ArgumentCaptor.forClass(RelayLog.class);
        verify(logRepo, times(2)).save(logs.capture());
        assertTrue(logs.getAllValues().stream().anyMatch(l -> l.type() == LogType.ERROR));
        assertTrue(logs.getAllValues().stream().anyMatch(l -> l.type() == LogType.CONSUME));
    }

    @Test
    void nonRetryableErrorReturnsImmediatelyWithoutRetry() {
        Channel c1 = channel(1L, 0); // autoBan=0 → 即便 401 也不禁用
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of()))).thenReturn(c1);
        UpstreamResponse err400 = UpstreamResponse.of(400, Map.of(),
                "{\"error\":{\"message\":\"bad request\"}}".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err400);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        assertEquals(400, result.statusCode());
        // 不可重试：只调一次上游，不再换渠道。
        verify(upstreamHttpPort, times(1)).send(any());
        verify(selectUseCase, times(1)).selectChannel(anyString(), anyString(), any());
        // 不结算（无成功）。
        verify(userQuotaAccount, never()).debit(anyLong(), any());
        // 错误响应体不含上游凭证。
        assertTrue(new String(result.body()).contains("upstream request rejected (400)"));
    }

    @Test
    void autoBanHitDisablesChannelOn401() {
        Channel c1 = channel(1L, 1); // autoBan=1 + 401 → 自动禁用
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of()))).thenReturn(c1);
        UpstreamResponse err401 = UpstreamResponse.of(401, Map.of(),
                "{\"error\":{\"message\":\"Bearer sk-leak invalid\"}}".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err401);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        assertEquals(401, result.statusCode());
        // 渠道被自动禁用并落库。
        ArgumentCaptor<Channel> saved = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepo, times(1)).save(saved.capture());
        assertEquals(ChannelStatus.AUTO_DISABLED, saved.getValue().status());
        // 脱敏：错误响应不泄露上游 token。
        assertTrue(new String(result.body()).contains("[redacted]")
                || !new String(result.body()).contains("sk-leak"));
    }

    @Test
    void retryExhaustionReturnsLastErrorWhenNoMoreChannels() {
        Channel c1 = channel(1L, 1);
        // 首次给 c1，排除后无更多渠道 → 抛 NoAvailableChannelException（CH-5 耗尽）。
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of()))).thenReturn(c1);
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of(1L))))
                .thenThrow(new NoAvailableChannelException("exhausted"));
        UpstreamResponse err500 = UpstreamResponse.of(500, Map.of(), "boom".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err500);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        // 重试耗尽态：返回最后一次上游错误（500），不抛异常。
        assertEquals(500, result.statusCode());
        verify(upstreamHttpPort, times(1)).send(any());
        verify(userQuotaAccount, never()).debit(anyLong(), any());
    }

    @Test
    void firstSelectionNoChannelStillThrows() {
        when(selectUseCase.selectChannel(eq("default"), anyString(), eq(Set.of())))
                .thenThrow(new NoAvailableChannelException("none"));
        org.junit.jupiter.api.Assertions.assertThrows(NoAvailableChannelException.class,
                () -> useCase.forward(PATH, BODY, auth));
    }
}
