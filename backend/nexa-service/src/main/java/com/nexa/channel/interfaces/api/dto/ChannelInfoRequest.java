package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.channel.domain.vo.MultiKeyMode;

/**
 * 渠道多 Key 信息入参 DTO（接口层，对齐 openapi ChannelInfo，F-2020）。
 *
 * <p>承载创建/编辑渠道时的多 Key 配置入参。转换为领域值对象 {@link ChannelInfo}（归一在值对象内）。</p>
 *
 * @param isMultiKey            是否多 Key
 * @param multiKeySize          Key 数量
 * @param multiKeyMode          轮询模式（random/polling）
 * @param multiKeyPollingIndex  轮询游标
 */
public record ChannelInfoRequest(
        @JsonProperty("is_multi_key") Boolean isMultiKey,
        @JsonProperty("multi_key_size") Integer multiKeySize,
        @JsonProperty("multi_key_mode") String multiKeyMode,
        @JsonProperty("multi_key_polling_index") Integer multiKeyPollingIndex) {

    /**
     * 转换为领域多 Key 信息值对象（null 入参归一为缺省）。
     *
     * @return 领域值对象
     */
    public ChannelInfo toDomain() {
        return new ChannelInfo(
                isMultiKey != null && isMultiKey,
                multiKeySize == null ? 0 : multiKeySize,
                MultiKeyMode.fromWire(multiKeyMode),
                multiKeyPollingIndex == null ? 0 : multiKeyPollingIndex);
    }
}
