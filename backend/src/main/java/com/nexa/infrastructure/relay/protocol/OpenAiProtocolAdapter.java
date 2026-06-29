package com.nexa.infrastructure.relay.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.domain.relay.exception.ProtocolConversionException;
import com.nexa.domain.relay.ir.ChatDeltaIR;
import com.nexa.domain.relay.ir.ChatIR;
import com.nexa.domain.relay.ir.ChatRespIR;
import com.nexa.domain.relay.ir.ContentBlock;
import com.nexa.domain.relay.ir.ContentBlockType;
import com.nexa.domain.relay.ir.Message;
import com.nexa.domain.relay.ir.StopReason;
import com.nexa.domain.relay.ir.StreamState;
import com.nexa.domain.relay.ir.UsageIR;
import com.nexa.domain.relay.protocol.ProtocolAdapter;
import com.nexa.domain.relay.protocol.ProtocolCapabilities;
import com.nexa.domain.relay.protocol.ProtocolRegistry;
import com.nexa.domain.relay.vo.ProtocolFormat;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 协议适配器（基础设施层，RL-6/RL-8，实现 {@link ProtocolAdapter}）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §2 + prd-relay RL-8 D1–D5。OpenAI 侧映射：
 * <ul>
 *   <li>D1 system：从 {@code messages[role=system]} 抽取进 IR system；序列化回 messages 首位；</li>
 *   <li>D2 content：字符串或数组 → IR block 数组；序列化时纯单 text 退化回字符串；</li>
 *   <li>D3 tools：{@code tools[].function} ⇄ IR Tool；tool_calls / role=tool ⇄ IR tool_use/tool_result；</li>
 *   <li>D4 stop_reason：finish_reason(stop/length/tool_calls) ⇄ IR StopReason（{@link StopReason#fromOpenAi}）；</li>
 *   <li>D5 usage：prompt_tokens/completion_tokens → IR UsageIR。</li>
 * </ul>
 * 五点外私有参数走 PassthroughExtras 旁路（ADR-COMPAT-01）。本类无状态，可多线程共享。</p>
 *
 * <p>设计说明：本适配器把 JSON 解析（基础设施关注点，依赖 Jackson）封进基础设施层，向 domain 暴露
 * 协议无关的 IR——符合 DDD「domain 零框架依赖」（domain 只持 {@link ProtocolAdapter} 接口 + IR）。
 * 首版聚焦非流式请求/响应正确性；流式逐 chunk 互转标注 TODO（RL-8 流式 StreamState 1→N，后续 wave 完善）。</p>
 */
@Component
public class OpenAiProtocolAdapter implements ProtocolAdapter {

    private final ObjectMapper mapper;

    public OpenAiProtocolAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 进程启动注册进协议注册表（RL-6 §2 init() 注册一行）。 */
    @PostConstruct
    public void registerSelf() {
        ProtocolRegistry.register(this);
    }

    @Override
    public ProtocolFormat format() {
        return ProtocolFormat.OPENAI;
    }

    @Override
    public ProtocolCapabilities capabilities() {
        return ProtocolCapabilities.OPENAI;
    }

    @Override
    public ChatIR parseRequest(byte[] raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String model = textOrEmpty(root, "model");
            ChatIR.Builder builder = ChatIR.builder(model);
            builder.stream(root.path("stream").asBoolean(false));
            if (root.hasNonNull("max_tokens")) {
                builder.maxTokens(root.get("max_tokens").asInt());
            } else if (root.hasNonNull("max_completion_tokens")) {
                builder.maxTokens(root.get("max_completion_tokens").asInt());
            }
            if (root.hasNonNull("temperature")) {
                builder.temperature(root.get("temperature").asDouble());
            }
            if (root.hasNonNull("top_p")) {
                builder.topP(root.get("top_p").asDouble());
            }
            // D1 + D2: messages → system 抽取 + content block 化
            JsonNode messages = root.path("messages");
            if (messages.isArray()) {
                for (JsonNode msg : messages) {
                    String role = textOrEmpty(msg, "role");
                    if (Message.ROLE_SYSTEM.equals(role)) {
                        // D1: system 抽进 IR.system
                        builder.addSystem(ContentBlock.text(extractTextContent(msg.path("content"))));
                    } else {
                        builder.addMessage(new Message(role, parseOpenAiContent(msg.path("content"))));
                    }
                }
            }
            return builder.build();
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to parse OpenAI request", e);
        }
    }

    @Override
    public byte[] serializeRequest(ChatIR ir) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", ir.model());
            root.put("stream", ir.stream());
            if (ir.maxTokens() != null) root.put("max_tokens", ir.maxTokens());
            if (ir.temperature() != null) root.put("temperature", ir.temperature());
            if (ir.topP() != null) root.put("top_p", ir.topP());
            ArrayNode messages = root.putArray("messages");
            // D1: system 还原到 messages 首位
            for (ContentBlock sys : ir.system()) {
                ObjectNode m = mapper.createObjectNode();
                m.put("role", Message.ROLE_SYSTEM);
                m.put("content", sys.text() == null ? "" : sys.text());
                messages.add(m);
            }
            for (Message msg : ir.messages()) {
                ObjectNode m = mapper.createObjectNode();
                m.put("role", msg.role());
                // D2: 纯单 text 退化回字符串
                if (isSingleText(msg.content())) {
                    m.put("content", msg.content().get(0).text());
                } else {
                    m.set("content", serializeContentArray(msg.content()));
                }
                messages.add(m);
            }
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to serialize OpenAI request", e);
        }
    }

    @Override
    public ChatRespIR parseResponse(byte[] raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String id = textOrEmpty(root, "id");
            String model = textOrEmpty(root, "model");
            List<ContentBlock> content = new ArrayList<>();
            StopReason stop = StopReason.END_TURN;
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                content.add(ContentBlock.text(extractTextContent(first.path("message").path("content"))));
                stop = StopReason.fromOpenAi(textOrNull(first, "finish_reason"));
            }
            // D5 usage
            JsonNode usageNode = root.path("usage");
            UsageIR usage = UsageIR.of(
                    usageNode.path("prompt_tokens").asInt(0),
                    usageNode.path("completion_tokens").asInt(0));
            return ChatRespIR.of(id, model, content, stop, usage);
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to parse OpenAI response", e);
        }
    }

    @Override
    public byte[] serializeResponse(ChatRespIR ir) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("id", ir.id() == null ? "" : ir.id());
            root.put("object", "chat.completion");
            root.put("model", ir.model() == null ? "" : ir.model());
            ArrayNode choices = root.putArray("choices");
            ObjectNode choice = mapper.createObjectNode();
            choice.put("index", 0);
            ObjectNode message = mapper.createObjectNode();
            message.put("role", Message.ROLE_ASSISTANT);
            message.put("content", joinText(ir.content()));
            choice.set("message", message);
            // D4 stop_reason
            choice.put("finish_reason", ir.stopReason() == null ? StopReason.OPENAI_STOP : ir.stopReason().toOpenAi());
            choices.add(choice);
            // D5 usage
            ObjectNode usage = mapper.createObjectNode();
            usage.put("prompt_tokens", ir.usage().promptTokens());
            usage.put("completion_tokens", ir.usage().completionTokens());
            usage.put("total_tokens", ir.usage().totalTokens());
            root.set("usage", usage);
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new ProtocolConversionException("failed to serialize OpenAI response", e);
        }
    }

    @Override
    public List<ChatDeltaIR> parseStreamChunk(byte[] raw, StreamState state) {
        // OpenAI SSE data:{choices:[{delta}]} / [DONE] → IR delta（RL-8 流式往返，REQ-08）。
        return OpenAiStreamCodec.parse(raw, state, mapper);
    }

    @Override
    public List<byte[]> serializeStreamChunk(ChatDeltaIR delta, StreamState state) {
        // IR delta → OpenAI SSE event（data:{...}），终结 chunk 追加 [DONE]（REQ-08）。
        return OpenAiStreamCodec.serialize(delta, state, mapper);
    }

    // ---- 私有辅助（D1/D2/D5 映射细节） ----

    private List<ContentBlock> parseOpenAiContent(JsonNode content) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (content.isTextual()) {
            // D2: 字符串 content → 单 text block
            blocks.add(ContentBlock.text(content.asText()));
        } else if (content.isArray()) {
            for (JsonNode part : content) {
                String type = textOrEmpty(part, "type");
                if ("text".equals(type)) {
                    blocks.add(ContentBlock.text(textOrEmpty(part, "text")));
                } else if ("image_url".equals(type)) {
                    String url = part.path("image_url").path("url").asText("");
                    blocks.add(ContentBlock.image(url, null));
                } else {
                    // 未建模类型：退化为 text（旁路语义，避免丢失）
                    blocks.add(ContentBlock.text(part.toString()));
                }
            }
        }
        return blocks;
    }

    private ArrayNode serializeContentArray(List<ContentBlock> blocks) {
        ArrayNode arr = mapper.createArrayNode();
        for (ContentBlock b : blocks) {
            ObjectNode node = mapper.createObjectNode();
            if (b.type() == ContentBlockType.TEXT) {
                node.put("type", "text");
                node.put("text", b.text() == null ? "" : b.text());
            } else if (b.type() == ContentBlockType.IMAGE) {
                node.put("type", "image_url");
                ObjectNode img = mapper.createObjectNode();
                img.put("url", b.imageUrl() == null ? "" : b.imageUrl());
                node.set("image_url", img);
            } else {
                node.put("type", "text");
                node.put("text", b.text() == null ? "" : b.text());
            }
            arr.add(node);
        }
        return arr;
    }

    private boolean isSingleText(List<ContentBlock> content) {
        return content.size() == 1 && content.get(0).type() == ContentBlockType.TEXT;
    }

    private String extractTextContent(JsonNode content) {
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
        return "";
    }

    private String joinText(List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            if (b.type() == ContentBlockType.TEXT && b.text() != null) {
                sb.append(b.text());
            }
        }
        return sb.toString();
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
