package com.nexa.relay.infrastructure.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.relay.domain.ir.ChatIR;
import com.nexa.relay.domain.ir.ChatRespIR;
import com.nexa.relay.domain.ir.ContentBlock;
import com.nexa.relay.domain.ir.StopReason;
import com.nexa.relay.domain.ir.Tool;
import com.nexa.relay.domain.ir.UsageIR;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClaudeProtocolAdapter 单元测试（RL-8 D1–D5 非流式双向往返，REQ-07）。
 *
 * <p>纯 JUnit（直接 new ObjectMapper，不起 Spring）。覆盖 D1 顶层 system / D2 content block 数组 /
 * D3 tools(input_schema)+tool_use/tool_result / D4 stop_reason / D5 usage(input/output_tokens)。</p>
 */
class ClaudeProtocolAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaudeProtocolAdapter adapter = new ClaudeProtocolAdapter(mapper);

    @Test
    void d1_systemFromTopLevel() {
        String json = "{\"model\":\"claude-3\",\"max_tokens\":100,"
                + "\"system\":\"you are helpful\","
                + "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}";
        ChatIR ir = adapter.parseRequest(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, ir.system().size());
        assertEquals("you are helpful", ir.system().get(0).text());
        assertEquals(1, ir.messages().size());
        assertEquals("user", ir.messages().get(0).role());
        assertEquals(100, ir.maxTokens());
    }

    @Test
    void d1_systemRestoredToTopLevelOnSerialize() throws Exception {
        ChatIR ir = ChatIR.builder("claude-3")
                .addSystem(ContentBlock.text("sys prompt"))
                .addMessage(com.nexa.relay.domain.ir.Message.ofText("user", "hello"))
                .build();
        byte[] out = adapter.serializeRequest(ir);
        JsonNode root = mapper.readTree(out);
        assertEquals("sys prompt", root.path("system").asText());
        // content 恒为 block 数组（D2）。
        assertTrue(root.path("messages").get(0).path("content").isArray());
        // max_tokens 必填补缺省。
        assertTrue(root.path("max_tokens").asInt() > 0);
    }

    @Test
    void d4_d5_responseParse() {
        String json = "{\"id\":\"msg_1\",\"model\":\"claude-3\",\"type\":\"message\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"answer\"}],"
                + "\"stop_reason\":\"max_tokens\","
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":8}}";
        ChatRespIR resp = adapter.parseResponse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(StopReason.MAX_TOKENS, resp.stopReason());
        assertEquals(12, resp.usage().promptTokens());
        assertEquals(8, resp.usage().completionTokens());
        assertEquals("answer", resp.content().get(0).text());
    }

    @Test
    void d4_d5_responseSerialize() throws Exception {
        ChatRespIR ir = ChatRespIR.of("msg_2", "claude-3",
                List.of(ContentBlock.text("hi there")), StopReason.TOOL_USE, UsageIR.of(5, 9));
        byte[] out = adapter.serializeResponse(ir);
        JsonNode root = mapper.readTree(out);
        assertEquals("message", root.path("type").asText());
        assertEquals("tool_use", root.path("stop_reason").asText());
        assertEquals(5, root.path("usage").path("input_tokens").asInt());
        assertEquals(9, root.path("usage").path("output_tokens").asInt());
        assertTrue(root.path("content").isArray());
    }

    @Test
    void d3_toolsRoundTrip() throws Exception {
        Tool tool = new Tool("get_weather", "get weather",
                Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        ChatIR ir = ChatIR.builder("claude-3")
                .tools(List.of(tool))
                .addMessage(com.nexa.relay.domain.ir.Message.ofText("user", "weather?"))
                .build();
        byte[] out = adapter.serializeRequest(ir);
        JsonNode root = mapper.readTree(out);
        JsonNode t = root.path("tools").get(0);
        assertEquals("get_weather", t.path("name").asText());
        // Anthropic 用 input_schema（非 OpenAI parameters）。
        assertEquals("object", t.path("input_schema").path("type").asText());

        // 反向解析回 IR。
        ChatIR back = adapter.parseRequest(out);
        assertEquals(1, back.tools().size());
        assertEquals("get_weather", back.tools().get(0).name());
    }

    @Test
    void d1_multiBlockSystemSerializesAsArray() throws Exception {
        // 多块 system 应序列化为 text 数组（覆盖 system().size()>1 分支），再反解析回 IR 无丢失。
        ChatIR ir = ChatIR.builder("claude-3")
                .addSystem(ContentBlock.text("rule A"))
                .addSystem(ContentBlock.text("rule B"))
                .addMessage(com.nexa.relay.domain.ir.Message.ofText("user", "hi"))
                .build();
        byte[] out = adapter.serializeRequest(ir);
        JsonNode root = mapper.readTree(out);
        assertTrue(root.path("system").isArray());
        assertEquals(2, root.path("system").size());
        assertEquals("rule A", root.path("system").get(0).path("text").asText());
        assertEquals("rule B", root.path("system").get(1).path("text").asText());

        ChatIR back = adapter.parseRequest(out);
        assertEquals(2, back.system().size());
        assertEquals("rule B", back.system().get(1).text());
    }

    @Test
    void d2_d4_d5_responseFullRoundTrip() {
        // serializeResponse → parseResponse 全往返：content/stop_reason/usage 不丢失。
        ChatRespIR original = ChatRespIR.of("msg_rt", "claude-3",
                List.of(ContentBlock.text("hello"), ContentBlock.text("world")),
                StopReason.MAX_TOKENS, UsageIR.of(11, 22));
        byte[] wire = adapter.serializeResponse(original);
        ChatRespIR back = adapter.parseResponse(wire);
        assertEquals("msg_rt", back.id());
        assertEquals("claude-3", back.model());
        assertEquals(StopReason.MAX_TOKENS, back.stopReason());
        assertEquals(11, back.usage().promptTokens());
        assertEquals(22, back.usage().completionTokens());
        assertEquals(2, back.content().size());
        assertEquals("hello", back.content().get(0).text());
        assertEquals("world", back.content().get(1).text());
    }

    @Test
    void d3_toolUseAndResultBlocks() {
        String json = "{\"model\":\"claude-3\",\"max_tokens\":50,\"messages\":["
                + "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\"calc\",\"input\":{\"x\":1}}]},"
                + "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"tu_1\",\"content\":\"42\"}]}]}";
        ChatIR ir = adapter.parseRequest(json.getBytes(StandardCharsets.UTF_8));
        ContentBlock toolUse = ir.messages().get(0).content().get(0);
        assertEquals(com.nexa.relay.domain.ir.ContentBlockType.TOOL_USE, toolUse.type());
        assertEquals("calc", toolUse.toolName());
        ContentBlock toolResult = ir.messages().get(1).content().get(0);
        assertEquals(com.nexa.relay.domain.ir.ContentBlockType.TOOL_RESULT, toolResult.type());
        assertEquals("42", toolResult.toolResult());
        assertFalse(toolResult.toolError());
    }
}
