package com.nexa.relay.domain.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * IR 消息（一条对话消息，role + content blocks，IR D2 统一）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §4.2。{@code Role} 取 user/assistant/tool（system
 * 单独走 {@link ChatIR#system()} 字段，承载 D1 system 位置统一）。content 为 block 数组（D2）。</p>
 *
 * @param role    user / assistant / tool（小写线值，与协议一致）
 * @param content 内容块数组（不可变，可能为空但非 null）
 */
public record Message(String role, List<ContentBlock> content) {

    /** 常用 role 常量。 */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
    public static final String ROLE_SYSTEM = "system";

    public Message {
        Objects.requireNonNull(role, "message role must not be null");
        content = content == null ? List.of() : Collections.unmodifiableList(content);
    }

    /** 单文本工厂（最常见路径）。 */
    public static Message ofText(String role, String text) {
        return new Message(role, List.of(ContentBlock.text(text == null ? "" : text)));
    }
}
