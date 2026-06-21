package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.application.UpdateChannelCommand;

/**
 * 编辑渠道请求 DTO（接口层，对齐 openapi ChannelUpdateRequest = ChannelCreateRequest + id，F-2016）。
 *
 * <p>内嵌创建请求字段 + 必填 id。接口层只做协议绑定 + 透传，校验在领域聚合（充血）。
 * key 可选更新（null/空白=保留原 key，由聚合处理）。</p>
 *
 * @param id   渠道 id（必填，由用例校验）
 * @param body 渠道字段（复用创建请求结构）
 */
public record ChannelUpdateRequest(
        @JsonProperty("id") Long id,
        @JsonProperty("type") Integer type,
        @JsonProperty("key") String key,
        @JsonProperty("models") String models,
        @JsonProperty("name") String name,
        @JsonProperty("group") String group,
        @JsonProperty("priority") Long priority,
        @JsonProperty("weight") Integer weight,
        @JsonProperty("auto_ban") Integer autoBan,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("model_mapping") String modelMapping,
        @JsonProperty("status_code_mapping") String statusCodeMapping,
        @JsonProperty("param_override") String paramOverride,
        @JsonProperty("header_override") String headerOverride,
        @JsonProperty("tag") String tag,
        @JsonProperty("channel_info") ChannelInfoRequest channelInfo) {

    /**
     * 转换为编辑命令。
     *
     * @return 编辑命令
     */
    public UpdateChannelCommand toCommand() {
        // 复用创建请求的覆写合并逻辑（保持 setting 承载方式一致），取其归一后的 setting。
        ChannelCreateRequest base = new ChannelCreateRequest(
                type, key, models, name, group, priority, weight, autoBan, baseUrl,
                modelMapping, statusCodeMapping, paramOverride, headerOverride, tag, channelInfo);
        String setting = base.toCommand().setting();
        return new UpdateChannelCommand(
                id, type, key, models, name, group, priority, weight, autoBan, baseUrl,
                modelMapping, statusCodeMapping, setting,
                tag, channelInfo == null ? null : channelInfo.toDomain());
    }
}
