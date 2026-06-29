package com.nexa.relay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.relay.domain.model.RelayLog;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.vo.LogType;
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
    private UserQuotaAccount userQuotaAccount;
    private RelayForwardUseCase useCase;
    private RelayAuthContext auth;

    @BeforeEach
    void setUp() {
        UserModelAliasRepository l1Repo = mock(UserModelAliasRepository.class);
        logRepo = mock(RelayLogRepository.class);
        upstreamHttpPort = mock(UpstreamHttpPort.class);
        KeyLimitGuard keyLimitGuard = mock(KeyLimitGuard.class);
        PublicModelRepository publicModelRepo = mock(PublicModelRepository.class);
        userQuotaAccount = mock(UserQuotaAccount.class);

        when(l1Repo.findTargetByAlias(any(), anyString())).thenReturn(Optional.empty());
        when(publicModelRepo.findByPublicName(anyString())).thenReturn(Optional.empty());

        useCase = new RelayForwardUseCase(l1Repo, logRepo, upstreamHttpPort,
                new ObjectMapper(), keyLimitGuard, publicModelRepo, userQuotaAccount,
                groupCode -> Optional.empty(),
                (groupCode, userId, tokenId, model) -> true,
                // 账号选择端口：返回一个 openai 账号（passthrough OpenAI→OpenAI）。
                new com.nexa.relay.domain.port.AccountSelectionPort() {
                    @Override public Optional<com.nexa.relay.domain.port.SelectedAccount> selectAccount(
                            String requestedModel, String platform, java.util.Set<Long> excludeAccountIds) {
                        java.util.Set<Long> excluded = excludeAccountIds == null ? java.util.Set.of() : excludeAccountIds;
                        if (excluded.contains(1L)) {
                            return Optional.empty();
                        }
                        return Optional.of(new com.nexa.relay.domain.port.SelectedAccount(
                                1L, "{\"key\":\"sk-key1\"}", "https://up1", "openai",
                                java.math.BigDecimal.ONE, null, "gpt-4o", null, 0));
                    }
                    @Override public void markRateLimited(long accountId, Long resetAt) { }
                    @Override public void markOverloaded(long accountId, Long until) { }
                },
                userId -> java.math.BigDecimal.ONE);
        // passthrough 流式累计 usage 依赖 ProtocolRegistry 命中 OpenAI 适配器。
        // 注册在 @PostConstruct registerSelf()，单元测试不走 Spring 容器须手动触发。
        new com.nexa.relay.infrastructure.protocol.OpenAiProtocolAdapter(new ObjectMapper()).registerSelf();
        auth = new RelayAuthContext(7L, "alice", "default", null, null);
    }

    @Test
    void streamForwardsChunksToClientAndBillsAtEnd() {

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

    /**
     * 真实链路漏钱场景①：passthrough 流中混入一个非标/控制块（codec 解析会抛 ProtocolConversionException）。
     *
     * <p>usage 解析仅是计费副作用，绝不能打断「原样转发」契约或吞掉流末计费。修复前该异常会冒泡为流失败 →
     * 不落计费 Log（漏钱）；修复后：坏块仍原样转发给客户，且流末按已累计 usage 落 1 条 is_stream=true 计费 Log。</p>
     */
    @Test
    void streamWithUnparseableChunkStillForwardsAndBills() {

        doAnswer(inv -> {
            UpstreamHttpPort.UpstreamStreamHandler h = inv.getArgument(1);
            h.onChunk("data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            // 含 usage 的合法块（计费口径来源）。
            h.onChunk(("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":9}}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            // 非标控制块：data: 后非合法 JSON，codec parse 会抛 ProtocolConversionException。
            h.onChunk("data: {not-json\n\n".getBytes(StandardCharsets.UTF_8));
            h.onChunk("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            h.onComplete(200);
            return null;
        }).when(upstreamHttpPort).stream(any(), any());

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        useCase.forwardStream(PATH, BODY, auth, sink);

        // 坏块仍原样转发（passthrough 不被 usage 解析打断）。
        String out = sink.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Hi"), "client must receive text chunk");
        assertTrue(out.contains("{not-json"), "unparseable chunk must still be forwarded verbatim");

        // 流末仍按已累计 usage(3/9) 落 1 条 is_stream=true 计费 Log（不因坏块漏钱）。
        ArgumentCaptor<RelayLog> logCap = ArgumentCaptor.forClass(RelayLog.class);
        verify(logRepo, times(1)).save(logCap.capture());
        RelayLog log = logCap.getValue();
        assertEquals(LogType.CONSUME, log.type());
        assertTrue(log.isStream(), "consume log must be marked stream=true");
        assertEquals(3, log.promptTokens());
        assertEquals(9, log.completionTokens());
    }

    /**
     * 真实链路漏钱场景②：已向客户写出 chunk 后上游中途断流（抛 UpstreamException）。
     *
     * <p>token 已交付给客户，必须按已累计 usage 落 1 条 is_stream=true 计费 Log（防漏钱）；修复前 catch 分支
     * 直接 return 不计费。已写出 chunk 故不重试（部分响应不可重放）。</p>
     */
    @Test
    void streamDroppedAfterPartialDeliveryStillBills() {

        doAnswer(inv -> {
            UpstreamHttpPort.UpstreamStreamHandler h = inv.getArgument(1);
            // 先写出带 usage 的内容块（token 已交付），再模拟上游断流。
            h.onChunk(("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}],"
                    + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            throw new com.nexa.relay.domain.exception.UpstreamException(
                    502, "upstream stream dropped mid-flight");
        }).when(upstreamHttpPort).stream(any(), any());

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        useCase.forwardStream(PATH, BODY, auth, sink);

        assertTrue(sink.toString(StandardCharsets.UTF_8).contains("partial"),
                "client must have received the partial chunk before drop");

        // 中途断流：先落 1 条 Type=5 错误 Log（既有 RL-3 行为），再因 token 已交付补落 1 条
        // is_stream=true 的 Type=2 计费 Log（修复点，防漏钱）。
        ArgumentCaptor<RelayLog> logCap = ArgumentCaptor.forClass(RelayLog.class);
        verify(logRepo, times(2)).save(logCap.capture());
        RelayLog consume = logCap.getAllValues().stream()
                .filter(l -> l.type() == LogType.CONSUME)
                .findFirst()
                .orElseThrow(() -> new AssertionError("must record a CONSUME log after partial delivery"));
        assertTrue(consume.isStream(), "consume log must be marked stream=true even on mid-stream drop");
        assertEquals(5, consume.promptTokens());
        assertEquals(2, consume.completionTokens());
    }
}

