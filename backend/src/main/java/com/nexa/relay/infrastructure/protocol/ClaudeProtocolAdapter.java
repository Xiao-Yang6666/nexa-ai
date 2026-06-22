package com.nexa.relay.infrastructure.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.relay.domain.exception.ProtocolConversionException;
import com.nexa.relay.domain.ir.ChatDeltaIR;
import com.nexa.relay.domain.ir.ChatIR;
import com.nexa.relay.domain.ir.ChatRespIR;
import com.nexa.relay.domain.ir.ContentBlock;
import com.nexa.relay.domain.ir.ContentBlockType;
import com.nexa.relay.domain.ir.Message;
import com.nexa.relay.domain.ir.StopReason;
import com.nexa.relay.domain.ir.StreamState;
import com.nexa.relay.domain.ir.Tool;
import com.nexa.relay.domain.ir.UsageIR;
import com.nexa.relay.domain.protocol.ProtocolAdapter;
import com.nexa.relay.domain.protocol.ProtocolCapabilities;
import com.nexa.relay.domain.protocol.ProtocolRegistry;
import com.nexa.relay.domain.vo.ProtocolFormat;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude 协议适配器（基础设施层，RL-6/RL-8，实现 {@link ProtocolAdapter}）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §2 + prd-relay RL-8 D1–D5。Anthropic 侧映射：
 * <ul>
 *   <li>D1 system：顶层 {@code system} 字段（string 或 block 数组）⇄ IR system；</li>
 *   <li>D2 content：恒为 block 数组 ⇄ IR block 数组；</li>
 *   <li>D3 tools：{@code tools[]{name,description,input_schema}} ⇄ IR {@link Tool}；
 *       assistant 的 {@code tool_use} block / user 的 {@code tool_result} block ⇄ IR TOOL_USE/TOOL_RESULT；</li>
 *   <li>D4 stop_reason：end_turn/max_tokens/stop_sequence/tool_use ⇄ IR {@link StopReason}；</li>
 *   <li>D5 usage：input_tokens/output_tokens ⇄ IR UsageIR(prompt/completion)。</li>
 * </ul>
 * 五点外私有参数走 {@link ChatIR#passthroughExtras()} 旁路（ADR-COMPAT-01）。本类无状态，可多线程共享。</p>
 *
 * <p>设计说明：JSON 解析（依赖 Jackson）封进基础设施层，向 domain 暴露协议无关 IR。Anthropic 必填
 * {@code max_tokens}：序列化请求时缺省补 {@link #DEFAULT_MAX_TOKENS}（避免上游 400）。本期非流式双向往返；
 * 流式逐 chunk 互转见 {@link #parseStreamChunk}/{@link #serializeStreamChunk}（RL-8 StreamState 1→N）。</p>
 */
@Component
public class ClaudeProtocolAdapter implements ProtocolAdapter {

    /** Anthropic {@code max_tokens} 必填，IR 未指定时的缺省补值（避免上游拒绝）。 */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final ObjectMapper mapper;

    public ClaudeProtocolAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 进程启动注册进协议注册表（RL-6 §2 init() 注册一行）。 */
    @PostConstruct
    public void registerSelf() {
        ProtocolRegistry.register(this);
    }

    @Override
    public ProtocolFormat format() {
        return ProtocolFormat.CLAUDE;
    }

    @Override
    public ProtocolCapabilities capabilities() {
        return ProtocolCapabilities.CLAUDE;
    }

    // ---- Inbound：Anthropic 请求 → IR ----

    @Override
    public ChatIR parseRequest(byte[] raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String model = textOrEmpty(root, "model");
            ChatIR.Builder builder = ChatIR.builder(model);
            builder.stream(root.path("stream").asBoolean(false));
            if (root.hasNonNull("max_tokens")) {
                builder.maxTokens(root.get("max_tokens").asInt());
            }
            if (root.hasNonNull("temperature")) {
                builder.temperature(root.get("temperature").asDouble());
            }
            if (root.hasNonNull("top_p")) {
                builder.topP(root.get("top_p").asDouble());
            }
            // D1: 顶层 system（string 或 block 数组）→ IR system。
            JsonNode systemNode = root.path("system");
            if (systemNode.isTextual()) {
                builder.addSystem(ContentBlock.text(systemNode.asText()));
            } else if (systemNode.isArray()) {
                for (JsonNode part : systemNode) {
                    if ("text".equals(textOrEmpty(part, "type"))) {
                        builder.addSystem(ContentBlock.text(textOrEmpty(part, "text")));
                    }
                }
            }
            // stop_sequences（IR 透传）。
            JsonNode stopSeqs = root.path("stop_sequences");
            if (stopSeqs.isArray()) {
                List<String> seqs = new ArrayList<>();
                stopSeqs.forEach(n -> seqs.add(n.asText()));
                builder.stopSequences(seqs);
            }
            // D3: tools（input_schema → IR jsonSchema）。
            JsonNode toolsNode = root.path("tools");
            if (toolsNode.isArray()) {
                List<Tool> tools = new ArrayList<>();
                for (JsonNode t : toolsNode) {
                    tools.add(parseTool(t));
                }
                builder.tools(tools);
            }
            // D2: messages（content 恒为 block 数组）。
            JsonNode messages = root.path("messages");
            if (messages.isArray()) {
                for (JsonNode msg : messages) {
                    String role = textOrEmpty(msg, "role");
                    builder.addMessage(new Message(role, parseClaudeContent(msg.path("content"))));
                }
            }
            return builder.build();
        } catch (ProtocolConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to parse Claude request", e);
        }
    }

    // ---- Outbound：IR → Anthropic 请求 ----

    @Override
    public byte[] serializeRequest(ChatIR ir) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", ir.model());
            root.put("stream", ir.stream());
            // D1: system 还原到顶层（多块拼接为 text 数组，单块退化为字符串）。
            if (!ir.system().isEmpty()) {
                if (ir.system().size() == 1) {
                    root.put("system", ir.system().get(0).text() == null ? "" : ir.system().get(0).text());
                } else {
                    ArrayNode sysArr = root.putArray("system");
                    for (ContentBlock sys : ir.system()) {
                        ObjectNode s = mapper.createObjectNode();
                        s.put("type", "text");
                        s.put("text", sys.text() == null ? "" : sys.text());
                        sysArr.add(s);
                    }
                }
            }
            // Anthropic max_tokens 必填：IR 未指定补缺省。
            root.put("max_tokens", ir.maxTokens() != null ? ir.maxTokens() : DEFAULT_MAX_TOKENS);
            if (ir.temperature() != null) root.put("temperature", ir.temperature());
            if (ir.topP() != null) root.put("top_p", ir.topP());
            if (!ir.stopSequences().isEmpty()) {
                ArrayNode seqs = root.putArray("stop_sequences");
                ir.stopSequences().forEach(seqs::add);
            }
            // D3: tools（IR jsonSchema → input_schema）。
            if (!ir.tools().isEmpty()) {
                ArrayNode toolsArr = root.putArray("tools");
                for (Tool tool : ir.tools()) {
                    toolsArr.add(serializeTool(tool));
                }
            }
            // D2: messages（content 恒为 block 数组）。
            ArrayNode messages = root.putArray("messages");
            for (Message msg : ir.messages()) {
                ObjectNode m = mapper.createObjectNode();
                m.put("role", msg.role());
                m.set("content", serializeContentArray(msg.content()));
                messages.add(m);
            }
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to serialize Claude request", e);
        }
    }

    // ---- 响应回转：Anthropic 响应 → IR ----

    @Override
    public ChatRespIR parseResponse(byte[] raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String id = textOrEmpty(root, "id");
            String model = textOrEmpty(root, "model");
            // D2: content 恒为 block 数组 → IR blocks。
            List<ContentBlock> content = parseClaudeContent(root.path("content"));
            // D4: stop_reason → IR。
            StopReason stop = StopReason.fromAnthropic(textOrNull(root, "stop_reason"));
            // D5: usage(input_tokens/output_tokens) → IR(prompt/completion)。
            JsonNode usageNode = root.path("usage");
            UsageIR usage = UsageIR.of(
                    usageNode.path("input_tokens").asInt(0),
                    usageNode.path("output_tokens").asInt(0));
            return ChatRespIR.of(id, model, content, stop, usage);
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to parse Claude response", e);
        }
    }

    // ---- 响应回转：IR → Anthropic 响应 ----

    @Override
    public byte[] serializeResponse(ChatRespIR ir) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("id", ir.id() == null ? "" : ir.id());
            root.put("type", "message");
            root.put("role", Message.ROLE_ASSISTANT);
            root.put("model", ir.model() == null ? "" : ir.model());
            // D2: content 恒为 block 数组。
            root.set("content", serializeContentArray(ir.content()));
            // D4: stop_reason。
            root.put("stop_reason", ir.stopReason() == null
                    ? StopReason.ANTHROPIC_END_TURN : ir.stopReason().toAnthropic());
            root.putNull("stop_sequence");
            // D5: usage(input_tokens/output_tokens)。
            ObjectNode usage = mapper.createObjectNode();
            usage.put("input_tokens", ir.usage().promptTokens());
            usage.put("output_tokens", ir.usage().completionTokens());
            root.set("usage", usage);
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to serialize Claude response", e);
        }
    }

    // ---- 流式（RL-8 StreamState 1→N，REQ-08） ----

    @Override
    public List<ChatDeltaIR> parseStreamChunk(byte[] raw, StreamState state) {
        return ClaudeStreamCodec.parse(raw, state, mapper);
    }

    @Override
    public List<byte[]> serializeStreamChunk(ChatDeltaIR delta, StreamState state) {
        return ClaudeStreamCodec.serialize(delta, state, mapper);
    }

    // ---- 私有辅助（D2/D3 映射细节） ----

    /** Anthropic content（恒为 block 数组）→ IR block 列表。 */
    private List<ContentBlock> parseClaudeContent(JsonNode content) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (content.isTextual()) {
            // 兼容：少数客户端用字符串 content。
            blocks.add(ContentBlock.text(content.asText()));
            return blocks;
        }
        if (!content.isArray()) {
            return blocks;
        }
        for (JsonNode part : content) {
            String type = textOrEmpty(part, "type");
            switch (type) {
                case "text" -> blocks.add(ContentBlock.text(textOrEmpty(part, "text")));
                case "image" -> {
                    JsonNode src = part.path("source");
                    // Anthropic image source: {type:base64, media_type, data} 或 {type:url, url}。
                    String url = src.has("url") ? src.path("url").asText("")
                            : src.path("data").asText("");
                    String mediaType = src.path("media_type").asText(null);
                    blocks.add(ContentBlock.image(url, mediaType));
                }
                case "tool_use" -> blocks.add(ContentBlock.toolUse(
                        textOrEmpty(part, "id"),
                        textOrEmpty(part, "name"),
                        jsonToMap(part.path("input"))));
                case "tool_result" -> blocks.add(ContentBlock.toolResult(
                        textOrEmpty(part, "tool_use_id"),
                        extractToolResultText(part.path("content")),
                        part.path("is_error").asBoolean(false)));
                default -> blocks.add(ContentBlock.text(part.toString())); // 未建模：旁路退化
            }
        }
        return blocks;
    }

    /** IR block 列表 → Anthropic content block 数组（恒为数组）。 */
    private ArrayNode serializeContentArray(List<ContentBlock> blocks) {
        ArrayNode arr = mapper.createArrayNode();
        for (ContentBlock b : blocks) {
            ObjectNode node = mapper.createObjectNode();
            switch (b.type()) {
                case TEXT -> {
                    node.put("type", "text");
                    node.put("text", b.text() == null ? "" : b.text());
                }
                case IMAGE -> {
                    node.put("type", "image");
                    ObjectNode source = mapper.createObjectNode();
                    String url = b.imageUrl() == null ? "" : b.imageUrl();
                    if (url.startsWith("data:") || (b.imageMediaType() != null && !url.startsWith("http"))) {
                        source.put("type", "base64");
                        if (b.imageMediaType() != null) source.put("media_type", b.imageMediaType());
                        source.put("data", url);
                    } else {
                        source.put("type", "url");
                        source.put("url", url);
                    }
                    node.set("source", source);
                }
                case TOOL_USE -> {
                    node.put("type", "tool_use");
                    node.put("id", b.toolUseId() == null ? "" : b.toolUseId());
                    node.put("name", b.toolName() == null ? "" : b.toolName());
                    node.set("input", mapToJson(b.toolInput()));
                }
                case TOOL_RESULT -> {
                    node.put("type", "tool_result");
                    node.put("tool_use_id", b.toolUseId() == null ? "" : b.toolUseId());
                    node.put("content", b.toolResult() == null ? "" : b.toolResult());
                    if (b.toolError()) node.put("is_error", true);
                }
                default -> {
                    node.put("type", "text");
                    node.put("text", b.text() == null ? "" : b.text());
                }
            }
            arr.add(node);
        }
        return arr;
    }

    /** Anthropic tool {name,description,input_schema} → IR Tool。 */
    private Tool parseTool(JsonNode t) {
        String name = textOrEmpty(t, "name");
        String description = textOrNull(t, "description");
        Map<String, Object> schema = jsonToMap(t.path("input_schema"));
        return new Tool(name, description, schema);
    }

    /** IR Tool → Anthropic tool（jsonSchema → input_schema）。 */
    private ObjectNode serializeTool(Tool tool) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", tool.name());
        if (tool.description() != null) node.put("description", tool.description());
        node.set("input_schema", mapToJson(tool.jsonSchema()));
        return node;
    }

    /** tool_result content（Anthropic 可为 string 或 block 数组）→ 扁平文本。 */
    private String extractToolResultText(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(textOrEmpty(part, "type"))) {
                    sb.append(textOrEmpty(part, "text"));
                }
            }
            return sb.toString();
        }
        return content.isMissingNode() || content.isNull() ? "" : content.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new LinkedHashMap<>();
        }
        return mapper.convertValue(node, Map.class);
    }

    private JsonNode mapToJson(Map<String, Object> map) {
        if (map == null) {
            return mapper.createObjectNode();
        }
        return mapper.valueToTree(map);
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
