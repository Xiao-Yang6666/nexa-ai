package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.application.CreateChannelCommand;

/**
 * 创建渠道请求 DTO（接口层，对齐 openapi ChannelCreateRequest，F-2016）。
 *
 * <p>接口层只做协议绑定 + 透传到应用命令，校验/归一在领域聚合（充血）。param_override/header_override
 * 为 F-2025 覆写入参，随 setting 透传（覆写在 setting JSON 内承载，避免 schema 膨胀）。</p>
 *
 * @param type              渠道 type（必填，由聚合校验）
 * @param key               上游凭证（必填，敏感）
 * @param models            支持模型集（必填）
 * @param name              渠道名（可空）
 * @param group             分组（可空→default）
 * @param priority          优先级（可空→0）
 * @param weight            权重（可空→0）
 * @param autoBan           自动禁用开关（可空→1）
 * @param baseUrl           上游 BaseURL（可空）
 * @param modelMapping      模型映射 JSON（可空）
 * @param statusCodeMapping 状态码映射 JSON（可空）
 * @param paramOverride     请求体覆写 JSON（F-2025，可空）
 * @param headerOverride    请求头覆写 JSON（F-2025，可空）
 * @param tag               标签（可空）
 * @param channelInfo       多 Key 信息（可空）
 */
public record ChannelCreateRequest(
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
     * 转换为创建命令（覆写入参合入 setting，多 Key 信息转领域值对象）。
     *
     * @return 创建命令
     */
    public CreateChannelCommand toCommand() {
        return new CreateChannelCommand(
                type, key, models, name, group, priority, weight, autoBan, baseUrl,
                modelMapping, statusCodeMapping, mergeSetting(),
                tag, channelInfo == null ? null : channelInfo.toDomain());
    }

    /**
     * 合并 F-2025 覆写入参为 setting JSON（param/header 覆写承载于 setting）。
     *
     * <p>当存在任一覆写时，包装为 {@code {"param_override":..,"header_override":..}} JSON 串；
     * 否则返回 null（无附加设置）。简单串拼，避免引入额外序列化依赖（值已是 JSON 串）。</p>
     *
     * @return setting JSON 串（可空）
     */
    private String mergeSetting() {
        boolean hasParam = paramOverride != null && !paramOverride.isBlank();
        boolean hasHeader = headerOverride != null && !headerOverride.isBlank();
        if (!hasParam && !hasHeader) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        if (hasParam) {
            sb.append("\"param_override\":").append(paramOverride);
        }
        if (hasHeader) {
            if (hasParam) {
                sb.append(",");
            }
            sb.append("\"header_override\":").append(headerOverride);
        }
        sb.append("}");
        return sb.toString();
    }
}
