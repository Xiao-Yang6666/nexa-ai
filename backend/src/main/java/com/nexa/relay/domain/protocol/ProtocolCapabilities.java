package com.nexa.relay.domain.protocol;

/**
 * 协议能力声明值对象（注册 + 扩展机制，COMPAT-LAYER-ARCHITECTURE §2.4）。
 *
 * <p>能力裁决：转换前用 {@code inAdapter.capabilities() ∩ targetAdapter.capabilities()} 做兼容性预检，
 * 能力缺口（如入站用 tools 但目标协议 {@code tools=false}）按降级/拒绝规则处理。</p>
 *
 * @param streaming 支持 SSE
 * @param tools     支持 tool/function calling
 * @param vision    支持图像 content block
 * @param embedding 支持 embedding 请求
 * @param audio     支持音频
 * @param image     支持图像生成
 */
public record ProtocolCapabilities(
        boolean streaming,
        boolean tools,
        boolean vision,
        boolean embedding,
        boolean audio,
        boolean image
) {

    /** OpenAI 能力（本期 chat 侧：streaming+tools+vision）。 */
    public static final ProtocolCapabilities OPENAI = new ProtocolCapabilities(true, true, true, true, false, true);

    /** Claude 能力（streaming+tools+vision，无 embedding/image gen）。 */
    public static final ProtocolCapabilities CLAUDE = new ProtocolCapabilities(true, true, true, false, false, false);
}
