package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.domain.vo.ChannelInfo;

/**
 * 渠道多 Key 信息视图（接口层 DTO，对齐 openapi ChannelInfo）。
 *
 * <p>仅承载多 Key 元信息（不含任何 key 明文）。字段名 snake_case 对齐契约。</p>
 *
 * @param isMultiKey            是否多 Key
 * @param multiKeySize          Key 数量
 * @param multiKeyMode          轮询模式（random/polling）
 * @param multiKeyPollingIndex  轮询游标
 */
public record ChannelInfoView(
        @JsonProperty("is_multi_key") boolean isMultiKey,
        @JsonProperty("multi_key_size") int multiKeySize,
        @JsonProperty("multi_key_mode") String multiKeyMode,
        @JsonProperty("multi_key_polling_index") int multiKeyPollingIndex) {

    /**
     * 由领域值对象组装视图。
     *
     * @param info 多 Key 信息值对象
     * @return 视图 DTO
     */
    public static ChannelInfoView from(ChannelInfo info) {
        return new ChannelInfoView(
                info.multiKey(), info.multiKeySize(), info.mode().wire(), info.pollingIndex());
    }
}
