package com.nexa.domain.relay.ir;

import java.util.Map;

/**
 * 内容块（IR D2 统一表示，不可变值对象）。
 *
 * <p>承载文本/图像/工具调用/工具结果四类语义，序列化时由 {@link com.nexa.domain.relay.protocol.ProtocolAdapter}
 * 反向还原到目标协议形态：
 * <ul>
 *   <li>OpenAI 序列化：纯单 text 退化为字符串（COMPAT-LAYER-DATA-OBJECTS §4.2）；</li>
 *   <li>Anthropic 序列化：恒为 block 数组。</li>
 * </ul>
 * </p>
 *
 * <p>采用工厂方法构造避免组合歧义；工具相关字段使用 {@code Map<String,Object>} 表达 JSON 子树
 * （domain 层零框架依赖，Map 是 JDK 原生类型）。</p>
 *
 * @param type        块类型（D2 决定）
 * @param text        文本内容（type=TEXT 时使用，可空）
 * @param imageUrl    图像 URL 或 base64 data URL（type=IMAGE 时使用，可空）
 * @param imageMediaType 图像 MIME（如 image/png；可空，缺省由序列化方推断）
 * @param toolUseId   工具调用 ID（type=TOOL_USE/TOOL_RESULT 时使用）
 * @param toolName    工具名（type=TOOL_USE 时使用）
 * @param toolInput   工具入参（type=TOOL_USE 时使用，JSON 对象）
 * @param toolResult  工具结果（type=TOOL_RESULT 时使用，OpenAI 是 string、Anthropic 是 block 数组兼容 string）
 * @param toolError   工具结果是否表示错误（Anthropic is_error，OpenAI 无字段对等）
 */
public record ContentBlock(
        ContentBlockType type,
        String text,
        String imageUrl,
        String imageMediaType,
        String toolUseId,
        String toolName,
        Map<String, Object> toolInput,
        String toolResult,
        boolean toolError
) {

    /** 文本块工厂（最常见，IR D2 统一形态）。 */
    public static ContentBlock text(String text) {
        return new ContentBlock(ContentBlockType.TEXT, text, null, null, null, null, null, null, false);
    }

    /** 图像块工厂（OpenAI image_url ⇄ Anthropic image source.url/base64）。 */
    public static ContentBlock image(String imageUrl, String mediaType) {
        return new ContentBlock(ContentBlockType.IMAGE, null, imageUrl, mediaType, null, null, null, null, false);
    }

    /** 工具调用块工厂（assistant 侧，IR D3）。 */
    public static ContentBlock toolUse(String id, String name, Map<String, Object> input) {
        return new ContentBlock(ContentBlockType.TOOL_USE, null, null, null, id, name, input, null, false);
    }

    /** 工具结果块工厂（user 侧 / OpenAI role=tool 消息 ⇄ Anthropic tool_result）。 */
    public static ContentBlock toolResult(String toolUseId, String result, boolean isError) {
        return new ContentBlock(ContentBlockType.TOOL_RESULT, null, null, null, toolUseId, null, null, result, isError);
    }
}
