package com.nexa.relay.domain.vo;

/**
 * 渠道类型码 → 目标协议格式映射（RL-6 头尾决策的「尾」侧协议判定）。
 *
 * <p>领域规则来源：prd-relay RL-6（inFmt vs targetProto 头尾对比）+ new-api ChannelType 常量。
 * 渠道 {@code type} 是开放整数枚举，本系统只需识别「该渠道上游说哪种协议」以决定是否走 IR 转换：
 * <ul>
 *   <li>Anthropic 原生类（type=14）→ {@link ProtocolFormat#CLAUDE}；</li>
 *   <li>其余（OpenAI 兼容口径，type=1/3/8/...）→ {@link ProtocolFormat#OPENAI}（默认，保持现网直通行为）。</li>
 * </ul>
 * 默认归到 OPENAI 是安全选择：绝大多数上游走 OpenAI 兼容协议，未识别类型回落 OpenAI 不阻断
 * （与 {@code RelayInfo.computePassthrough} 配合：inFmt==OPENAI 时直通）。新增协议类（Gemini 等）
 * 待对应 ProtocolAdapter 落地后在此扩展映射。</p>
 */
public final class ChannelProtocolMapping {

    /** Anthropic 原生渠道 type 码（new-api ChannelTypeAnthropic=14）。 */
    public static final int ANTHROPIC = 14;

    private ChannelProtocolMapping() {
    }

    /**
     * 按渠道 type 码判定目标上游协议。
     *
     * @param channelType 渠道 type 整数码
     * @return 目标协议（未识别回落 {@link ProtocolFormat#OPENAI}）
     */
    public static ProtocolFormat protocolOf(int channelType) {
        if (channelType == ANTHROPIC) {
            return ProtocolFormat.CLAUDE;
        }
        return ProtocolFormat.OPENAI;
    }
}
