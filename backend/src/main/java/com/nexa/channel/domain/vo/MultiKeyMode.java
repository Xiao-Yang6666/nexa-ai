package com.nexa.channel.domain.vo;

/**
 * 渠道多 Key 轮询模式值对象（对齐 openapi ChannelInfo.multi_key_mode：随机/轮询）。
 *
 * <p>领域规则来源：F-2020 多 Key 渠道。多 Key 渠道在多个上游凭证间选取时的策略：
 * <ul>
 *   <li>{@link #RANDOM} —— 随机选 key。</li>
 *   <li>{@link #POLLING} —— 轮询（按 polling_index 顺序）。</li>
 * </ul>
 * 持久化为可读字符串（"random"/"polling"），解析时未知值回退 {@link #RANDOM}（宽容兼容）。</p>
 */
public enum MultiKeyMode {

    /** 随机。 */
    RANDOM("random"),

    /** 轮询。 */
    POLLING("polling");

    private final String wire;

    MultiKeyMode(String wire) {
        this.wire = wire;
    }

    /** @return 线上/持久化字符串表示 */
    public String wire() {
        return wire;
    }

    /**
     * 由字符串解析模式（大小写不敏感）。
     *
     * <p>null/空白/未知值回退 {@link #RANDOM}——多 Key 模式为非关键配置，宽容兼容旧数据，不报错。</p>
     *
     * @param raw 原始字符串
     * @return 对应模式（缺省 RANDOM）
     */
    public static MultiKeyMode fromWire(String raw) {
        if (raw == null) {
            return RANDOM;
        }
        String v = raw.trim().toLowerCase();
        for (MultiKeyMode m : values()) {
            if (m.wire.equals(v)) {
                return m;
            }
        }
        return RANDOM;
    }
}
