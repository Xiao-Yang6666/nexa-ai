package com.nexa.relay.infrastructure.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.relay.domain.ir.ChatDeltaIR;
import com.nexa.relay.domain.ir.StopReason;
import com.nexa.relay.domain.ir.StreamState;
import com.nexa.relay.domain.ir.UsageIR;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式 SSE 编解码单元测试（RL-8 StreamState 1→N，REQ-08）。
 *
 * <p>覆盖：OpenAI SSE chunk 解析（text/finish_reason/[DONE]）、Claude 多 event 序列化（message_start →
 * content_block_delta → message_delta/message_stop）、OpenAI 流 → Claude 流端到端转换。</p>
 */
class StreamCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiProtocolAdapter openai = new OpenAiProtocolAdapter(mapper);
    private final ClaudeProtocolAdapter claude = new ClaudeProtocolAdapter(mapper);

    @Test
    void openAiParseTextDelta() {
        StreamState state = new StreamState();
        byte[] chunk = "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                .getBytes(StandardCharsets.UTF_8);
        List<ChatDeltaIR> deltas = openai.parseStreamChunk(chunk, state);
        assertEquals(1, deltas.size());
        assertEquals("Hel", deltas.get(0).deltaContent().get(0).text());
    }

    @Test
    void openAiParseDoneEmitsTerminal() {
        StreamState state = new StreamState();
        // 先记录 finish_reason，再 [DONE]。
        openai.parseStreamChunk(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n".getBytes(StandardCharsets.UTF_8),
                state);
        List<ChatDeltaIR> done = openai.parseStreamChunk(
                "data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8), state);
        assertEquals(1, done.size());
        assertEquals(StopReason.END_TURN, done.get(0).stopReason());
        assertTrue(state.isMessageStopped());
    }

    @Test
    void claudeSerializeFirstDeltaEmitsMessageStartAndBlockStart() {
        StreamState state = new StreamState();
        List<byte[]> events = claude.serializeStreamChunk(ChatDeltaIR.textDelta("Hi"), state);
        // 首个 delta：message_start + content_block_start + content_block_delta（3 个 event）。
        assertEquals(3, events.size());
        String joined = join(events);
        assertTrue(joined.contains("message_start"));
        assertTrue(joined.contains("content_block_start"));
        assertTrue(joined.contains("content_block_delta"));
        assertTrue(joined.contains("\"text\":\"Hi\""));
    }

    @Test
    void claudeSerializeTerminalEmitsStopEvents() {
        StreamState state = new StreamState();
        state.markMessageStarted(); // 模拟已发首块
        List<byte[]> events = claude.serializeStreamChunk(
                ChatDeltaIR.done(StopReason.END_TURN, UsageIR.of(3, 7)), state);
        String joined = join(events);
        assertTrue(joined.contains("content_block_stop"));
        assertTrue(joined.contains("message_delta"));
        assertTrue(joined.contains("message_stop"));
        assertTrue(joined.contains("end_turn"));
    }

    @Test
    void openAiStreamToClaudeStreamEndToEnd() {
        // 上游 OpenAI 流 → 解析为 IR → 序列化为客户 Claude 流（跨协议 1→N）。
        StreamState upstream = new StreamState();
        StreamState client = new StreamState();
        StringBuilder clientOut = new StringBuilder();

        for (byte[] up : new byte[][]{
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n".getBytes(StandardCharsets.UTF_8),
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n".getBytes(StandardCharsets.UTF_8),
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n".getBytes(StandardCharsets.UTF_8),
                "data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)}) {
            for (ChatDeltaIR delta : openai.parseStreamChunk(up, upstream)) {
                for (byte[] outEvent : claude.serializeStreamChunk(delta, client)) {
                    clientOut.append(new String(outEvent, StandardCharsets.UTF_8));
                }
            }
        }
        String out = clientOut.toString();
        assertTrue(out.contains("message_start"));
        assertTrue(out.contains("\"text\":\"Hello\""));
        assertTrue(out.contains("\"text\":\" world\""));
        assertTrue(out.contains("message_stop"));
        // 累计 usage（OpenAI 本例未带 usage，故为 0；至少不抛错且终结 event 出现）。
        assertFalse(out.isEmpty());
    }

    private String join(List<byte[]> events) {
        StringBuilder sb = new StringBuilder();
        for (byte[] e : events) {
            sb.append(new String(e, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
