package com.nexa.domain.relay.ir;

/**
 * 内容块类型（IR D2，对齐 OpenAI/Anthropic 共有 + 工具往返）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §4.2 + RL-8 D3。
 * IR 把两协议的 content 统一为 block 数组：
 * <ul>
 *   <li>{@link #TEXT}：文本块（OpenAI 字符串/{type:"text"} ⇄ Anthropic {type:"text"}）；</li>
 *   <li>{@link #IMAGE}：图像块（OpenAI {type:"image_url"} ⇄ Anthropic {type:"image"}）；</li>
 *   <li>{@link #TOOL_USE}：工具调用块（assistant 侧；OpenAI tool_calls ⇄ Anthropic tool_use）；</li>
 *   <li>{@link #TOOL_RESULT}：工具结果块（user 侧；OpenAI role=tool 消息 ⇄ Anthropic tool_result）。</li>
 * </ul>
 * </p>
 */
public enum ContentBlockType {
    TEXT,
    IMAGE,
    TOOL_USE,
    TOOL_RESULT
}
