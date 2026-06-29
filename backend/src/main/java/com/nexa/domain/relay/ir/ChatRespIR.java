package com.nexa.domain.relay.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 响应 IR（Chat/Completions 响应的协议无关中间表示，RL-6 / RL-8 核心数据对象）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §4.3 + prd-relay RL-8 D4/D5。
 * <ul>
 *   <li>D4 stop_reason：IR 枚举 {@link StopReason}，双向映射 OpenAI finish_reason ⇄ Anthropic stop_reason；</li>
 *   <li>D5 usage：IR {@link UsageIR}，统一 prompt/completion tokens 字段名。</li>
 * </ul>
 * </p>
 *
 * @param id         响应 ID（OpenAI chatcmpl-xxx / Anthropic msg_xxx）
 * @param model      模型名（上游回报值）
 * @param role       响应角色（通常 assistant）
 * @param content    内容块列表（可为空）
 * @param stopReason 停止原因（D4 IR 枚举）
 * @param usage      token 用量（D5 统一字段名）
 */
public record ChatRespIR(
        String id,
        String model,
        String role,
        List<ContentBlock> content,
        StopReason stopReason,
        UsageIR usage
) {

    public ChatRespIR {
        content = content == null ? List.of() : Collections.unmodifiableList(content);
        Objects.requireNonNull(usage, "usage must not be null (use UsageIR.ZERO if unknown)");
    }

    /** 构造便捷工厂。 */
    public static ChatRespIR of(String id, String model, List<ContentBlock> content, StopReason stopReason, UsageIR usage) {
        return new ChatRespIR(id, model, Message.ROLE_ASSISTANT, content, stopReason, usage);
    }
}
