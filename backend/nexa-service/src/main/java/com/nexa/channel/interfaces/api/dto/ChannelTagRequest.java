package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 按 tag 批量启停渠道请求 DTO（接口层，对齐 openapi ChannelTagRequest，F-2019）。
 *
 * @param tag 标签（必填，空白由用例拒绝）
 */
public record ChannelTagRequest(@JsonProperty("tag") String tag) {
}
