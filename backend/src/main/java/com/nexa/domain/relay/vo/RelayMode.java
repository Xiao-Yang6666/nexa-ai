package com.nexa.domain.relay.vo;

/**
 * 中继模式枚举（RL-2 {@code Path2RelayMode} 产出，复用 new-api {@code relay/constant} 语义）。
 *
 * <p>描述请求落到哪条中继业务链：chat/completions、completions(legacy)、messages(claude)、
 * embeddings、responses、edits(独立 legacy)、realtime、未实现(variations)。与 {@link ProtocolFormat}
 * 配合：RelayMode 指「做什么业务」，ProtocolFormat 指「用什么协议解析/序列化」。</p>
 *
 * <p>领域规则来源：prd-relay.md RL-2 主流程 rd_p1~rd_p8 有序匹配。</p>
 */
public enum RelayMode {

    /** 默认 chat/completions（rd_def）。 */
    CHAT_COMPLETIONS,

    /** legacy completions（{@code /v1/completions}）。 */
    COMPLETIONS,

    /** Anthropic messages（rd_claude）。 */
    CLAUDE_MESSAGES,

    /** Gemini 原生（rd_gemini）。 */
    GEMINI_GENERATE,

    /** embeddings 后缀匹配（rd_emb）。 */
    EMBEDDINGS,

    /** responses（rd_resp）。 */
    RESPONSES,

    /** responses/compact（rd_compact，必须先于 RESPONSES 匹配）。 */
    RESPONSES_COMPACTION,

    /** realtime WebSocket 升级（rd_ws）。 */
    REALTIME,

    /** 独立 legacy edits（{@code /v1/edits}，区别于 /images/edits，rd_edits）。 */
    EDITS,

    /** 模型列表（{@code /v1/models}）。 */
    MODELS_LIST,

    /** 未实现（{@code /v1/images/variations} → RelayNotImplemented，rd_ni）。 */
    NOT_IMPLEMENTED
}
