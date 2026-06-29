package com.nexa.domain.relay.protocol;

import com.nexa.domain.relay.ir.ChatDeltaIR;
import com.nexa.domain.relay.ir.ChatIR;
import com.nexa.domain.relay.ir.ChatRespIR;
import com.nexa.domain.relay.ir.StreamState;
import com.nexa.domain.relay.vo.ProtocolFormat;

import java.util.List;

/**
 * 协议适配器接口（RL-6 注册表 + IR 双向转换，领域接口，零框架依赖）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §2.3 签名级定义。每个协议（OpenAI/Claude/...）实现一个
 * adapter，在 {@link ProtocolRegistry} 注册后由管线调用。职责：
 * <ul>
 *   <li>Inbound：客户报文 → IR（{@link #parseRequest}）；</li>
 *   <li>Outbound：IR → 目标协议报文（{@link #serializeRequest}）；</li>
 *   <li>响应回转：目标协议响应 → IR（{@link #parseResponse}）；IR → 客户入站协议响应（{@link #serializeResponse}）；</li>
 *   <li>流式：逐 chunk 互转（{@link #parseStreamChunk}/{@link #serializeStreamChunk}，可能 1→N SSE event）。</li>
 * </ul>
 * </p>
 *
 * <p>设计铁律（ADR-COMPAT-02）：OpenAI/Claude 实现内部可直接复用现有 convert 逻辑，本接口是"统一入口壳"。</p>
 */
public interface ProtocolAdapter {

    /** 该适配器代表的协议（注册键）。 */
    ProtocolFormat format();

    /** 协议能力声明（供能力预检用）。 */
    ProtocolCapabilities capabilities();

    /**
     * Inbound：客户报文原始字节 → IR。
     *
     * @param raw 原始 JSON body
     * @return 协议无关的 ChatIR
     */
    ChatIR parseRequest(byte[] raw);

    /**
     * Outbound：IR → 目标协议 JSON 字节（用于调上游）。
     *
     * @param ir ChatIR（model 恒为 B）
     * @return 目标协议 JSON
     */
    byte[] serializeRequest(ChatIR ir);

    /**
     * 上游响应（目标协议 JSON）→ IR。
     *
     * @param raw 上游响应 JSON
     * @return IR 响应
     */
    ChatRespIR parseResponse(byte[] raw);

    /**
     * IR 响应 → 客户入站协议 JSON。
     *
     * @param ir IR 响应
     * @return 序列化的 JSON
     */
    byte[] serializeResponse(ChatRespIR ir);

    /**
     * 流式：解析单个 SSE chunk（目标协议侧）。
     *
     * @param raw chunk 字节
     * @param state 流式上下文（mutable，跨 chunk 保持状态）
     * @return IR 增量列表（可为空）
     */
    List<ChatDeltaIR> parseStreamChunk(byte[] raw, StreamState state);

    /**
     * 流式：序列化 IR 增量为 SSE event 字节列表（客户入站协议侧，可能 1→N）。
     *
     * @param delta IR 增量
     * @param state 流式上下文
     * @return SSE event 字节列表
     */
    List<byte[]> serializeStreamChunk(ChatDeltaIR delta, StreamState state);
}
