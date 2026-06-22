package com.nexa.channel.domain.vo;

/**
 * 渠道多 Key 信息值对象（不可变，对齐 openapi ChannelInfo + DB-SCHEMA §3 channel_info JSONB）。
 *
 * <p>领域规则来源：F-2020 多 Key 渠道。承载渠道是否多 Key、Key 数量、轮询模式与轮询游标。
 * 作为 {@link com.nexa.channel.domain.model.Channel} 聚合的内嵌值对象，按值相等、构造后不可变。</p>
 *
 * <p>注：本值对象仅承载「多 Key 元信息」，不含任何 key 明文（key 在 Channel 聚合的敏感字段，
 * 绝不下发视图）。</p>
 *
 * @param multiKey       是否为多 Key 渠道（is_multi_key）
 * @param multiKeySize   Key 数量（multi_key_size，>=0）
 * @param mode           轮询模式（multi_key_mode）
 * @param pollingIndex   轮询游标（multi_key_polling_index，>=0）
 */
public record ChannelInfo(boolean multiKey, int multiKeySize, MultiKeyMode mode, int pollingIndex) {

    /**
     * 紧凑构造器：归一非负量与缺省模式（多 Key 元信息为非关键配置，宽容归一不报错）。
     */
    public ChannelInfo {
        if (multiKeySize < 0) {
            multiKeySize = 0;
        }
        if (pollingIndex < 0) {
            pollingIndex = 0;
        }
        if (mode == null) {
            mode = MultiKeyMode.RANDOM;
        }
    }

    /**
     * 单 Key 渠道的缺省信息（非多 Key）。
     *
     * @return 单 Key 缺省值对象
     */
    public static ChannelInfo single() {
        return new ChannelInfo(false, 0, MultiKeyMode.RANDOM, 0);
    }
}
