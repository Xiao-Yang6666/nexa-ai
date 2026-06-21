package com.nexa.relay.domain.vo;

/**
 * 协议格式枚举（入站协议「头」 + 目标供应商协议「尾」，复用 new-api {@code types.RelayFormat} 语义）。
 *
 * <p>对应 COMPAT-LAYER-ARCHITECTURE §2「协议适配器代表的协议」与 RL-2 路径分发产出的
 * {@code RelayFormat}。本期协议兼容层（RL-6/RL-8）只实现 {@link #OPENAI} 与 {@link #CLAUDE} 两个
 * 协议适配器，其余（{@link #GEMINI}/{@link #EMBEDDING}/{@link #OPENAI_RESPONSES}/...）为预留枚举位：
 * 注册表未命中时回落现有 per-channel 直转，不阻断现网（ADR-COMPAT-01/06）。</p>
 *
 * <p>领域规则来源：prd-relay.md RL-2 / RL-6；wire 值用于落 Log.inbound_protocol/upstream_protocol。</p>
 */
public enum ProtocolFormat {

    /** OpenAI 协议（{@code /v1/chat/completions} / {@code /v1/completions}）。 */
    OPENAI("openai"),

    /** Anthropic Claude 原生协议（{@code /v1/messages}）。 */
    CLAUDE("claude"),

    /** Google Gemini 原生协议（{@code /v1beta/models/*}，预留扩展位）。 */
    GEMINI("gemini"),

    /** OpenAI Responses（{@code /v1/responses}，预留）。 */
    OPENAI_RESPONSES("openai_responses"),

    /** OpenAI Responses Compaction（{@code /v1/responses/compact}，预留）。 */
    OPENAI_RESPONSES_COMPACTION("openai_responses_compaction"),

    /** OpenAI Realtime（{@code /v1/realtime}，WebSocket 升级，预留）。 */
    OPENAI_REALTIME("openai_realtime"),

    /** Embedding（任意 {@code *embeddings} 后缀，预留）。 */
    EMBEDDING("embedding");

    private final String wireValue;

    ProtocolFormat(String wireValue) {
        this.wireValue = wireValue;
    }

    /** @return 落库 / 可观测用的协议线值（如 {@code "openai"}） */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 本期协议兼容层（IR 转换）是否已实现该协议的 {@code ProtocolAdapter}。
     *
     * <p>仅 {@link #OPENAI}/{@link #CLAUDE} 为 true；其余协议注册表未命中，RL-6 回落 per-channel 直转
     * （ADR-COMPAT-01：首版仅 openai/claude）。</p>
     *
     * @return 是否在 compat 注册表内
     */
    public boolean isCompatSupported() {
        return this == OPENAI || this == CLAUDE;
    }

    /**
     * 按线值解析协议（不区分大小写）。
     *
     * @param wire 协议线值
     * @return 命中的协议，未知返回 null（调用方负责回落/拒绝）
     */
    public static ProtocolFormat fromWire(String wire) {
        if (wire == null) {
            return null;
        }
        for (ProtocolFormat f : values()) {
            if (f.wireValue.equalsIgnoreCase(wire)) {
                return f;
            }
        }
        return null;
    }
}
