package com.nexa.relay.infrastructure.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.relay.domain.exception.ProtocolConversionException;
import com.nexa.relay.domain.ir.ChatDeltaIR;
import com.nexa.relay.domain.ir.ChatIR;
import com.nexa.relay.domain.ir.ChatRespIR;
import com.nexa.relay.domain.ir.StreamState;
import com.nexa.relay.domain.protocol.ProtocolAdapter;
import com.nexa.relay.domain.protocol.ProtocolCapabilities;
import com.nexa.relay.domain.protocol.ProtocolRegistry;
import com.nexa.relay.domain.vo.ProtocolFormat;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Claude 协议适配器（基础设施层，RL-6/RL-8，实现 {@link ProtocolAdapter}）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §2 + prd-relay RL-8 D1–D5。Anthropic 侧映射：
 * <ul>
 *   <li>D1 system：从顶层 {@code system} 字段 → IR system；序列化回顶层 system；</li>
 *   <li>D2 content：恒为 block 数组；</li>
 *   <li>D3 tools：{@code tools[]{name,input_schema}} ⇄ IR Tool；tool_use/tool_result content blocks；</li>
 *   <li>D4 stop_reason：stop_reason(end_turn/max_tokens/stop_sequence/tool_use) ⇄ IR StopReason（{@link com.nexa.relay.domain.ir.StopReason#fromAnthropic}）；</li>
 *   <li>D5 usage：input_tokens/output_tokens → IR UsageIR。</li>
 * </ul>
 * 首版聚焦架构正确性；Claude 协议完整映射标注 TODO（后续 wave 完善，本期 OpenAI 已证明 IR 架构可行）。</p>
 */
@Component
public class ClaudeProtocolAdapter implements ProtocolAdapter {

    private final ObjectMapper mapper;

    public ClaudeProtocolAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

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

    @Override
    public ChatIR parseRequest(byte[] raw) {
        // TODO(W3+): 解析 Anthropic /v1/messages 请求，系统从顶层 system 取（D1），content 恒为 block 数组（D2）。
        throw new ProtocolConversionException("Claude parseRequest not yet implemented (W3+ TODO)");
    }

    @Override
    public byte[] serializeRequest(ChatIR ir) {
        // TODO(W3+): IR → Anthropic /v1/messages 请求，D1~D5 反向还原。
        throw new ProtocolConversionException("Claude serializeRequest not yet implemented (W3+ TODO)");
    }

    @Override
    public ChatRespIR parseResponse(byte[] raw) {
        // TODO(W3+): Anthropic 响应 → IR，D4 stop_reason 映射、D5 usage input/output → prompt/completion。
        throw new ProtocolConversionException("Claude parseResponse not yet implemented (W3+ TODO)");
    }

    @Override
    public byte[] serializeResponse(ChatRespIR ir) {
        // TODO(W3+): IR → Anthropic 响应，D4/D5 反向映射。
        throw new ProtocolConversionException("Claude serializeResponse not yet implemented (W3+ TODO)");
    }

    @Override
    public List<ChatDeltaIR> parseStreamChunk(byte[] raw, StreamState state) {
        // TODO(W3+): Anthropic 多 event (message_start/content_block_delta/message_delta/message_stop) → IR delta。
        return List.of();
    }

    @Override
    public List<byte[]> serializeStreamChunk(ChatDeltaIR delta, StreamState state) {
        // TODO(W3+): IR delta → Anthropic SSE 多 event，1→N。
        return List.of();
    }
}
