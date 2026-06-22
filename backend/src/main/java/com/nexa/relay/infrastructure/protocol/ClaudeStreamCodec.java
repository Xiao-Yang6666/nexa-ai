package com.nexa.relay.infrastructure.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.relay.domain.exception.ProtocolConversionException;
import com.nexa.relay.domain.ir.ChatDeltaIR;
import com.nexa.relay.domain.ir.ContentBlock;
import com.nexa.relay.domain.ir.StopReason;
import com.nexa.relay.domain.ir.StreamState;
import com.nexa.relay.domain.ir.UsageIR;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic SSE 流式编解码（RL-8 流式 StreamState 1→N，REQ-08）。
 *
 * <p>Anthropic 流式由多类 event 组成，需借 {@link StreamState} 跨 chunk 维护开闭/index/累计 usage：
 * <pre>
 *   message_start          → 记 input usage，标记 messageStarted
 *   content_block_start     → 打开一个 block（记录 index）
 *   content_block_delta     → text_delta 增量 → IR textDelta
 *   content_block_stop      → 关闭当前 block
 *   message_delta           → stop_reason + 累计 output usage
 *   message_stop            → 终结 → IR done(stopReason, usage)
 * </pre>
 * 序列化方向（IR delta → Anthropic event）：首个 delta 先补 message_start + content_block_start
 * （1→N）；中间 text → content_block_delta；终结 delta → content_block_stop + message_delta + message_stop。</p>
 *
 * <p>本类无状态（静态工具方法），状态全在传入的 {@link StreamState}。SSE 行格式：{@code event: <type>\n
 * data: <json>\n\n}。</p>
 */
final class ClaudeStreamCodec {

    private ClaudeStreamCodec() {
    }

    /** 解析单条 Anthropic SSE chunk（一个 event）→ IR delta 列表（可能为空）。 */
    static List<ChatDeltaIR> parse(byte[] raw, StreamState state, ObjectMapper mapper) {
        if (raw == null || raw.length == 0) {
            return List.of();
        }
        String chunk = new String(raw, StandardCharsets.UTF_8);
        List<ChatDeltaIR> deltas = new ArrayList<>();
        // 一个 chunk 可能含多行 event；逐 "data:" 行解析。
        for (String line : chunk.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String json = trimmed.substring("data:".length()).trim();
            if (json.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = mapper.readTree(json);
                String type = node.path("type").asText("");
                switch (type) {
                    case "message_start" -> {
                        state.markMessageStarted();
                        JsonNode u = node.path("message").path("usage");
                        if (!u.isMissingNode()) {
                            state.updateUsage(UsageIR.of(
                                    u.path("input_tokens").asInt(0),
                                    u.path("output_tokens").asInt(0)));
                        }
                    }
                    case "content_block_delta" -> {
                        JsonNode delta = node.path("delta");
                        if ("text_delta".equals(delta.path("type").asText(""))) {
                            String text = delta.path("text").asText("");
                            if (!text.isEmpty()) {
                                deltas.add(ChatDeltaIR.textDelta(text));
                            }
                        }
                    }
                    case "message_delta" -> {
                        // 累计 output usage + stop_reason（终态在 message_stop 才发 done）。
                        JsonNode u = node.path("usage");
                        if (!u.isMissingNode()) {
                            UsageIR prev = state.getCumulativeUsage();
                            state.updateUsage(UsageIR.of(
                                    prev.promptTokens(),
                                    u.path("output_tokens").asInt(prev.completionTokens())));
                        }
                        String sr = node.path("delta").path("stop_reason").asText(null);
                        if (sr != null) {
                            state.setStopReasonWire(sr);
                        }
                    }
                    case "message_stop" -> {
                        state.markMessageStopped();
                        StopReason reason = StopReason.fromAnthropic(state.getStopReasonWire());
                        deltas.add(ChatDeltaIR.done(reason, state.getCumulativeUsage()));
                    }
                    default -> {
                        // content_block_start / content_block_stop / ping：无 IR 产出。
                    }
                }
            } catch (Exception e) {
                throw new ProtocolConversionException("failed to parse Claude stream chunk", e);
            }
        }
        return deltas;
    }

    /** IR delta → Anthropic SSE event 字节列表（1→N）。 */
    static List<byte[]> serialize(ChatDeltaIR delta, StreamState state, ObjectMapper mapper) {
        List<byte[]> events = new ArrayList<>();
        try {
            // 首个 delta：补 message_start + content_block_start（index 0）。
            if (!state.isMessageStarted()) {
                state.markMessageStarted();
                ObjectNode msgStart = mapper.createObjectNode();
                msgStart.put("type", "message_start");
                ObjectNode message = msgStart.putObject("message");
                message.put("id", "msg_stream");
                message.put("type", "message");
                message.put("role", "assistant");
                message.putArray("content");
                message.putNull("stop_reason");
                ObjectNode startUsage = message.putObject("usage");
                startUsage.put("input_tokens", state.getCumulativeUsage().promptTokens());
                startUsage.put("output_tokens", 0);
                events.add(sse("message_start", msgStart, mapper));

                ObjectNode blockStart = mapper.createObjectNode();
                blockStart.put("type", "content_block_start");
                blockStart.put("index", 0);
                ObjectNode cb = blockStart.putObject("content_block");
                cb.put("type", "text");
                cb.put("text", "");
                events.add(sse("content_block_start", blockStart, mapper));
            }

            // 文本增量 → content_block_delta。
            String text = joinText(delta.deltaContent());
            if (!text.isEmpty()) {
                ObjectNode blockDelta = mapper.createObjectNode();
                blockDelta.put("type", "content_block_delta");
                blockDelta.put("index", state.getCurrentContentBlockIndex());
                ObjectNode d = blockDelta.putObject("delta");
                d.put("type", "text_delta");
                d.put("text", text);
                events.add(sse("content_block_delta", blockDelta, mapper));
            }

            // 终结 delta：content_block_stop + message_delta(stop_reason+usage) + message_stop。
            if (delta.stopReason() != null && !state.isMessageStopped()) {
                state.markMessageStopped();
                if (delta.usage() != null) {
                    state.updateUsage(delta.usage());
                }
                ObjectNode blockStop = mapper.createObjectNode();
                blockStop.put("type", "content_block_stop");
                blockStop.put("index", state.getCurrentContentBlockIndex());
                events.add(sse("content_block_stop", blockStop, mapper));

                ObjectNode msgDelta = mapper.createObjectNode();
                msgDelta.put("type", "message_delta");
                ObjectNode dlt = msgDelta.putObject("delta");
                dlt.put("stop_reason", delta.stopReason().toAnthropic());
                dlt.putNull("stop_sequence");
                ObjectNode usage = msgDelta.putObject("usage");
                usage.put("output_tokens", state.getCumulativeUsage().completionTokens());
                events.add(sse("message_delta", msgDelta, mapper));

                ObjectNode msgStop = mapper.createObjectNode();
                msgStop.put("type", "message_stop");
                events.add(sse("message_stop", msgStop, mapper));
            }
            return events;
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to serialize Claude stream chunk", e);
        }
    }

    private static byte[] sse(String event, ObjectNode data, ObjectMapper mapper) throws Exception {
        String payload = "event: " + event + "\n"
                + "data: " + mapper.writeValueAsString(data) + "\n\n";
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static String joinText(List<ContentBlock> blocks) {
        if (blocks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            if (b.text() != null) {
                sb.append(b.text());
            }
        }
        return sb.toString();
    }
}
