package com.nexa.infrastructure.relay.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.domain.relay.exception.ProtocolConversionException;
import com.nexa.domain.relay.ir.ChatDeltaIR;
import com.nexa.domain.relay.ir.ContentBlock;
import com.nexa.domain.relay.ir.StopReason;
import com.nexa.domain.relay.ir.StreamState;
import com.nexa.domain.relay.ir.UsageIR;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI SSE 流式编解码（RL-8 流式，REQ-08）。
 *
 * <p>OpenAI 流式格式：{@code data: {choices:[{delta:{content}}], usage?}}，每行一个 chunk，
 * 终结哨兵 {@code data: [DONE]}。{@code finish_reason} 出现在终结前的最后一个含 choices 的 chunk。
 * 相比 Anthropic 的多 event，OpenAI 流式较扁平，但仍借 {@link StreamState} 跨 chunk 维护：
 * <ul>
 *   <li>解析：text delta → IR textDelta；带 finish_reason 的 chunk → 记 stopReason；{@code [DONE]} → IR done；</li>
 *   <li>序列化：首/中间 delta → {@code data:{choices:[{delta:{role?,content}}]}}；终结 delta →
 *       带 finish_reason 的 chunk +（usage chunk）+ {@code data: [DONE]}（1→N）。</li>
 * </ul>
 * </p>
 */
final class OpenAiStreamCodec {

    private static final String DONE = "[DONE]";

    private OpenAiStreamCodec() {
    }

    /** 解析 OpenAI SSE chunk → IR delta 列表。 */
    static List<ChatDeltaIR> parse(byte[] raw, StreamState state, ObjectMapper mapper) {
        if (raw == null || raw.length == 0) {
            return List.of();
        }
        String chunk = new String(raw, StandardCharsets.UTF_8);
        List<ChatDeltaIR> deltas = new ArrayList<>();
        for (String line : chunk.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String json = trimmed.substring("data:".length()).trim();
            if (json.isEmpty()) {
                continue;
            }
            if (DONE.equals(json)) {
                // 终结哨兵：发 done（携带累计 usage + 已记录的 stopReason，缺省 END_TURN）。
                StopReason reason = StopReason.fromOpenAi(state.getStopReasonWire());
                deltas.add(ChatDeltaIR.done(reason, state.getCumulativeUsage()));
                state.markMessageStopped();
                continue;
            }
            try {
                JsonNode node = mapper.readTree(json);
                // usage（部分上游 stream_options.include_usage 时在末 chunk 返回）。
                JsonNode usageNode = node.path("usage");
                if (usageNode.isObject() && !usageNode.isNull()) {
                    state.updateUsage(UsageIR.of(
                            usageNode.path("prompt_tokens").asInt(0),
                            usageNode.path("completion_tokens").asInt(0)));
                }
                JsonNode choices = node.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode first = choices.get(0);
                    String text = first.path("delta").path("content").asText("");
                    if (!text.isEmpty()) {
                        deltas.add(ChatDeltaIR.textDelta(text));
                    }
                    String fr = first.path("finish_reason").asText(null);
                    if (fr != null && !fr.isEmpty() && !"null".equals(fr)) {
                        state.setStopReasonWire(fr);
                    }
                }
            } catch (Exception e) {
                throw new ProtocolConversionException("failed to parse OpenAI stream chunk", e);
            }
        }
        return deltas;
    }

    /** IR delta → OpenAI SSE event 字节列表（终结时追加 [DONE]，1→N）。 */
    static List<byte[]> serialize(ChatDeltaIR delta, StreamState state, ObjectMapper mapper) {
        List<byte[]> events = new ArrayList<>();
        try {
            boolean first = !state.isMessageStarted();
            if (first) {
                state.markMessageStarted();
            }
            String text = joinText(delta.deltaContent());

            // 文本增量（或首 chunk role）→ data:{choices:[{delta:{role?,content}}]}。
            if (!text.isEmpty() || first) {
                ObjectNode root = baseChunk(mapper);
                ArrayNode choices = root.putArray("choices");
                ObjectNode choice = mapper.createObjectNode();
                choice.put("index", 0);
                ObjectNode d = choice.putObject("delta");
                if (first) {
                    d.put("role", "assistant");
                }
                d.put("content", text);
                choice.putNull("finish_reason");
                choices.add(choice);
                events.add(sse(root, mapper));
            }

            // 终结 delta：finish_reason chunk +（usage chunk）+ [DONE]。
            if (delta.stopReason() != null && !state.isMessageStopped()) {
                state.markMessageStopped();
                if (delta.usage() != null) {
                    state.updateUsage(delta.usage());
                }
                ObjectNode finRoot = baseChunk(mapper);
                ArrayNode finChoices = finRoot.putArray("choices");
                ObjectNode finChoice = mapper.createObjectNode();
                finChoice.put("index", 0);
                finChoice.putObject("delta");
                finChoice.put("finish_reason", delta.stopReason().toOpenAi());
                finChoices.add(finChoice);
                // 末 chunk 带 usage（对齐 stream_options.include_usage 行为）。
                UsageIR u = state.getCumulativeUsage();
                ObjectNode usage = finRoot.putObject("usage");
                usage.put("prompt_tokens", u.promptTokens());
                usage.put("completion_tokens", u.completionTokens());
                usage.put("total_tokens", u.totalTokens());
                events.add(sse(finRoot, mapper));

                events.add(("data: " + DONE + "\n\n").getBytes(StandardCharsets.UTF_8));
            }
            return events;
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to serialize OpenAI stream chunk", e);
        }
    }

    private static ObjectNode baseChunk(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "chatcmpl-stream");
        root.put("object", "chat.completion.chunk");
        root.put("created", System.currentTimeMillis() / 1000L);
        return root;
    }

    private static byte[] sse(ObjectNode data, ObjectMapper mapper) throws Exception {
        return ("data: " + mapper.writeValueAsString(data) + "\n\n").getBytes(StandardCharsets.UTF_8);
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
