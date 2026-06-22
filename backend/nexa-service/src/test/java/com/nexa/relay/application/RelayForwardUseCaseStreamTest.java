package com.nexa.relay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelModelCostRepository;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.relay.domain.model.RelayLog;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.repository.PlatformModelMappingRepository;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.vo.LogType;
import com.nexa.routing.application.SelectRelayChannelUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RelayForwardUseCase 流式转发端到端单元测试（REQ-08 RL-8 SSE 1→N + 末尾计费）。
 *
 * <p>用 mock 桩驱动 {@link UpstreamHttpPort#stream}：模拟上游逐 chunk 回调 SSE 事件，验证
 * ① 上游流经 codec 解析 → IR → 序列化 → 写入客户 sink（passthrough OpenAI→OpenAI 链路）；
 * ② 流正常结束（onComplete）后按累计 usage 走双价记账，落 1 条 Type=2 消费 Log 且 stream=true。</p>
 */
class RelayForwardUseCaseStreamTest {

    private static final String PATH = "/v1/chat/completions";
    private static final byte[] BODY = "{\"model\":\"gpt-4o\",\"stream\":true}".getBytes(StandardCharsets.UTF_8);

    private UpstreamHttpPort upstreamHttpPort;
    private RelayLogRepository logRepo;
    private SelectRelayChannelUseCase selectUseCase;
    private UserQuotaAccount userQuotaAccount;
    private RelayForwardUseCase useCase;
    private RelayAuthContext auth;

    @BeforeEach
    void setUp() {
        PlatformModelMappingRepository l2Repo = mock(PlatformModelMappingRepository.class);
        UserModelAliasRepository l1Repo = mock(UserModelAliasRepository.class);
        logRepo = mock(RelayLogRepository.class);
        upstreamHttpPort = mock(UpstreamHttpPort.class);
        ChannelRepository channelRepo = mock(ChannelRepository.class);
        KeyLimitGuard keyLimitGuard = mock(KeyLimitGuard.class);
        selectUseCase = mock(SelectRelayChannelUseCase.class);
        PublicModelRepository publicModelRepo = mock(PublicModelRepository.class);
        ChannelModelCostRepository costRepo = mock(ChannelModelCostRepository.class);
        userQuotaAccount = mock(UserQuotaAccount.class);

        when(l1Repo.findTargetByAlias(any(), anyString())).thenReturn(Optional.empty());
        when(l2Repo.findUpstreamByPublicName(anyString())).thenReturn(Optional.empty());
        when(publicModelRepo.findByPublicName(anyString())).thenReturn(Optional.empty());
        when(costRepo.findByChannelAndUpstream(org.mockito.ArgumentMatchers.anyInt(), anyString()))
                .thenReturn(Optional.empty());

        useCase = new RelayForwardUseCase(l2Repo, l1Repo, logRepo, upstreamHttpPort, channelRepo,
                new ObjectMapper(), keyLimitGuard, selectUseCase, publicModelRepo, costRepo, userQuotaAccount,
                groupCode -> Optional.empty(),
                (groupCode, userId, tokenId) -> true);
        // passthrough 流式累计 usage 依赖 ProtocolRegistry 命中 OpenAI 适配器。
        // 注册在 @PostConstruct registerSelf()，单元测试不走 Spring 容器须手动触发。
        new com.nexa.relay.infrastructure.protocol.OpenAiProtocolAdapter(new ObjectMapper()).registerSelf();
        auth = new RelayAuthContext(7L, "alice", "default", null, null);
    }

    private Channel openAiChannel() {
        // type=1 → OpenAI 协议（passthrough，inFmt==targetProto）。
        return Channel.rehydrate(1L, 1, "sk-key1", ChannelStatus.ENABLED.code(), "ch1",
                0, "https://up1", "gpt-4o", "default", 0L, 1,
                java.math.BigDecimal.ZERO, 0L, null, null, null, "", null, null, null, 0L);
    }

    @Test
    void streamForwardsChunksToClientAndBillsAtEnd() {
        when(selectUseCase.selectChannel(eq("default"), anyString(), any())).thenReturn(openAiChannel());

        // 上游 SSE：两个文本块 + 带 usage 的末块 + [DONE]，逐块回调 handler。
        doAnswer(inv -> {
            UpstreamHttpPort.UpstreamStreamHandler h = inv.getArgument(1);
            h.onChunk("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            h.onChunk("data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            h.onChunk(("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":6}}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            h.onChunk("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            h.onComplete(200);
            return null;
        }).when(upstreamHttpPort).stream(any(), any());

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        useCase.forwardStream(PATH, BODY, auth, sink);

        // ① 上游流 → codec → 下游 SSE：客户收到 passthrough 转发的文本与 [DONE]。
        String out = sink.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Hel"), "client must receive first text chunk");
        assertTrue(out.contains("lo"), "client must receive second text chunk");
        assertTrue(out.contains("[DONE]"), "client must receive terminal sentinel");

        // ② 流末计费：按累计 usage(4/6) 落 1 条 Type=2 消费 Log，stream=true。
        ArgumentCaptor<RelayLog> logCap = ArgumentCaptor.forClass(RelayLog.class);
        verify(logRepo, times(1)).save(logCap.capture());
        RelayLog log = logCap.getValue();
        assertEquals(LogType.CONSUME, log.type());
        assertEquals(4, log.promptTokens());
        assertEquals(6, log.completionTokens());
        assertTrue(log.isStream(), "consume log must be marked stream=true");
        // 结算扣减发生一次（quotaSell>0）。
        verify(userQuotaAccount, times(1)).debit(anyLong(), any());
    }
}

