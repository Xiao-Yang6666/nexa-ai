package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 上游探测结果应用请求 DTO（接口层，对齐 openapi ChannelUpstreamApplyRequest，F-2026）。
 *
 * <p>承载勾选的上游模型集，覆盖式应用到渠道 models（空集合由聚合拒绝）。</p>
 *
 * @param models 勾选的上游模型集
 */
public record ChannelUpstreamApplyRequest(@JsonProperty("models") List<String> models) {
}
