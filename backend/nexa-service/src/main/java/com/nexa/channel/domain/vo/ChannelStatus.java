package com.nexa.channel.domain.vo;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;

/**
 * 渠道状态值对象（枚举，对齐 DB-SCHEMA §3 channels.status 与 new-api 渠道状态语义）。
 *
 * <p>领域规则来源：openapi /api/channel/tag/enable|disable 描述、F-2019：
 * <ul>
 *   <li>{@link #ENABLED}(1) —— 启用，可参与选渠路由。</li>
 *   <li>{@link #MANUALLY_DISABLED}(2) —— 手动禁用（按 tag disable 落此态，不自动恢复）。</li>
 *   <li>{@link #AUTO_DISABLED}(3) —— 自动禁用（探测/计费异常触发，可被自动恢复机制启用）。</li>
 * </ul>
 * 状态以整数持久化（与现网兼容），通过 {@link #fromCode(int)} 解析、{@link #code()} 落库。</p>
 */
public enum ChannelStatus {

    /** 启用（ChannelStatusEnabled=1）。 */
    ENABLED(1),

    /** 手动禁用（ChannelStatusManuallyDisabled=2，不自动恢复）。 */
    MANUALLY_DISABLED(2),

    /** 自动禁用（ChannelStatusAutoDisabled=3）。 */
    AUTO_DISABLED(3);

    private final int code;

    ChannelStatus(int code) {
        this.code = code;
    }

    /** @return 持久化整数码 */
    public int code() {
        return code;
    }

    /**
     * 由整数码解析状态。
     *
     * <p>未知码视为非法入参（不静默兜底），抛 {@link InvalidChannelParameterException}。</p>
     *
     * @param code 状态整数码
     * @return 对应状态
     * @throws InvalidChannelParameterException 未知状态码
     */
    public static ChannelStatus fromCode(int code) {
        for (ChannelStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new InvalidChannelParameterException("unknown channel status code: " + code);
    }

    /** @return 是否为启用态 */
    public boolean isEnabled() {
        return this == ENABLED;
    }
}
