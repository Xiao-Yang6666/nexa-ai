package com.nexa.infrastructure.relay.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.domain.relay.ir.ChatIR;
import com.nexa.domain.relay.ir.ChatRespIR;
import com.nexa.domain.relay.ir.ContentBlock;
import com.nexa.domain.relay.ir.Message;
import com.nexa.domain.relay.ir.StopReason;
import com.nexa.domain.relay.ir.UsageIR;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAiProtocolAdapter 单元测试（RL-8 D1 system 归位 / D2 content 退化 / D5 usage 归一）。
 *
 * <p>纯 JUnit（直接 new ObjectMapper，不起 Spring）。</p>
 */
class OpenAiProtocolAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiProtocolAdapter adapter = new OpenAiProtocolAdapter(mapper);

    @Test
    void d1_systemExtractedFromMessages() {
        // D1: OpenAI messages[role=system] → IR.system
        String json = "{\"model\":\"gpt-4\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"you are helpful\"},"
                + "{\"role\":\"user\",\"content\":\"hi\"}]}";
        ChatIR ir = adapter.parseRequest(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, ir.system().size());
        assertEquals("you are helpful", ir.system().get(0).text());
        assertEquals(1, ir.messages().size());
        assertEquals("user", ir.messages().get(0).role());
    }

    @Test
    void d2_stringContentBecomesBlockAndDegradesBack() {
        // D2: 字符串 content → block；序列化纯单 text 退化回字符串
        String json = "{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
        ChatIR ir = adapter.parseRequest(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, ir.messages().get(0).content().size());

        byte[] out = adapter.serializeRequest(ir);
        try {
            JsonNode root = mapper.readTree(out);
            JsonNode content = root.path("messages").get(0).path("content");
            // 纯单 text 退化回字符串（非数组）
            assertTrue(content.isTextual());
            assertEquals("hello", content.asText());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void d5_usageNormalized() {
        // D5: OpenAI usage.prompt_tokens/completion_tokens → IR UsageIR
        String json = "{\"id\":\"chatcmpl-1\",\"model\":\"gpt-4\","
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}";
        ChatRespIR resp = adapter.parseResponse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(10, resp.usage().promptTokens());
        assertEquals(5, resp.usage().completionTokens());
        assertEquals(StopReason.END_TURN, resp.stopReason());
    }

    @Test
    void responseSerializationRoundTrip() {
        ChatRespIR ir = ChatRespIR.of("id-1", "gpt-4",
                List.of(ContentBlock.text("answer")), StopReason.MAX_TOKENS, UsageIR.of(7, 3));
        byte[] out = adapter.serializeResponse(ir);
        try {
            JsonNode root = mapper.readTree(out);
            assertEquals("answer", root.path("choices").get(0).path("message").path("content").asText());
            assertEquals("length", root.path("choices").get(0).path("finish_reason").asText());
            assertEquals(7, root.path("usage").path("prompt_tokens").asInt());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
