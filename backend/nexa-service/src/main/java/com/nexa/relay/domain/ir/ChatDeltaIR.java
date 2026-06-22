package com.nexa.relay.domain.ir;

import java.util.List;

/**
 * 流式增量 IR（单 SSE chunk 的协议无关表示，RL-8 流式往返）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §4.3。Anthropic 多 event（message_start/content_block_delta/
 * message_delta/message_stop）与 OpenAI {@code data:{choices:[{delta}]}}+{@code [DONE]} 经 {@link StreamState}
 * 互转（{@code SerializeStreamChunk} 可能 1→N 个 SSE event）。</p>
 *
 * @param deltaContent 本 chunk 的内容块增量（可能只有部分 text，role 通常只在首 chunk 出现）
 * @param stopReason   若本 chunk 是终结 chunk，则携带 stopReason；否则为 null
 * @param usage        增量 usage（部分上游在中间 chunk 返回 usage；完整的在终结 chunk）
 */
public record ChatDeltaIR(
        List<ContentBlock> deltaContent,
        StopReason stopReason,
        UsageIR usage
) {

    /** 最小非空工厂（text chunk）。 */
    public static ChatDeltaIR textDelta(String text) {
        return new ChatDeltaIR(List.of(ContentBlock.text(text)), null, null);
    }

    /** 终结 chunk（带 stopReason）。 */
    public static ChatDeltaIR done(StopReason reason, UsageIR usage) {
        return new ChatDeltaIR(List.of(), reason, usage);
    }
}
