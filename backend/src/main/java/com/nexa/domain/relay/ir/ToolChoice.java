package com.nexa.domain.relay.ir;

import java.util.Objects;

/**
 * 工具选择策略（IR D3 ToolChoice，双向映射 OpenAI {@code tool_choice} ⇄ Anthropic {@code tool_choice}）。
 *
 * <p>支持四种语义（COMPAT-LAYER-DATA-OBJECTS §4.1）：
 * <ul>
 *   <li>{@link Mode#AUTO}：自动选择（OpenAI {@code "auto"} ⇄ Anthropic {@code {"type":"auto"}}）；</li>
 *   <li>{@link Mode#NONE}：禁用工具（OpenAI {@code "none"} ⇄ Anthropic 无对等，序列化时省略）；</li>
 *   <li>{@link Mode#REQUIRED}：必须用工具（OpenAI {@code "required"} ⇄ Anthropic {@code {"type":"any"}}）；</li>
 *   <li>{@link Mode#TOOL}：必须用指定工具（{@code toolName} 非空）。</li>
 * </ul>
 * </p>
 */
public record ToolChoice(Mode mode, String toolName) {

    public enum Mode { AUTO, NONE, REQUIRED, TOOL }

    public ToolChoice {
        Objects.requireNonNull(mode, "tool choice mode must not be null");
        if (mode == Mode.TOOL && (toolName == null || toolName.isBlank())) {
            throw new IllegalArgumentException("tool choice mode=TOOL requires non-empty toolName");
        }
    }

    public static ToolChoice auto() { return new ToolChoice(Mode.AUTO, null); }
    public static ToolChoice none() { return new ToolChoice(Mode.NONE, null); }
    public static ToolChoice required() { return new ToolChoice(Mode.REQUIRED, null); }
    public static ToolChoice tool(String name) { return new ToolChoice(Mode.TOOL, name); }
}
