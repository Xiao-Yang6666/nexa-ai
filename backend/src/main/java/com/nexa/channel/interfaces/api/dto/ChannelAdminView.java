package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.domain.model.Channel;

import java.math.BigDecimal;

/**
 * 渠道管理视图 DTO（接口层，对齐 openapi ChannelAdminView，AdminAuth）。
 *
 * <p><b>客户视图铁律 + 渠道凭证安全（强制）</b>：本视图为<b>管理端</b>渠道视图，<b>绝不下发渠道 key
 * 原始凭证</b>（openapi「key 脱敏/不全量下发」）——本 DTO 不含任何 key 字段。其余为管理运维所需的
 * 渠道属性（type/name/status/weight/baseUrl/models/group/priority/autoBan/balance/usedQuota/
 * 响应时间/测试时间/模型映射/状态码映射/tag/channelInfo/createdTime），由领域聚合裁剪组装。</p>
 *
 * <p>字段名 snake_case 对齐契约。balance 为渠道余额（管理端可见，非客户成本/利润，符合产品红线）。</p>
 *
 * @param id                渠道 id
 * @param type              渠道 type
 * @param name              渠道名
 * @param status            状态码（1 启用/2 手动禁用/3 自动禁用）
 * @param weight            权重
 * @param baseUrl           上游 BaseURL
 * @param models            支持模型集（逗号分隔）
 * @param group             分组
 * @param priority          优先级
 * @param autoBan           自动禁用开关
 * @param balance           余额（USD）
 * @param usedQuota         已用配额
 * @param responseTime      最近测试响应耗时 ms（可空）
 * @param testTime          最近测试时间 epoch 秒（可空）
 * @param modelMapping      模型映射 JSON（可空）
 * @param statusCodeMapping 状态码映射 JSON
 * @param tag               标签（可空）
 * @param channelInfo       多 Key 信息
 * @param createdTime       创建时间 epoch 秒（可空）
 */
public record ChannelAdminView(
        @JsonProperty("id") Long id,
        @JsonProperty("type") int type,
        @JsonProperty("name") String name,
        @JsonProperty("status") int status,
        @JsonProperty("weight") int weight,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("models") String models,
        @JsonProperty("group") String group,
        @JsonProperty("priority") long priority,
        @JsonProperty("auto_ban") int autoBan,
        @JsonProperty("balance") BigDecimal balance,
        @JsonProperty("used_quota") long usedQuota,
        @JsonProperty("response_time") Integer responseTime,
        @JsonProperty("test_time") Long testTime,
        @JsonProperty("model_mapping") String modelMapping,
        @JsonProperty("status_code_mapping") String statusCodeMapping,
        @JsonProperty("tag") String tag,
        @JsonProperty("channel_info") ChannelInfoView channelInfo,
        @JsonProperty("created_time") Long createdTime) {

    /**
     * 由领域聚合裁剪组装管理视图（剔除 key 等敏感凭证）。
     *
     * @param c 渠道聚合
     * @return 管理视图 DTO（无 key）
     */
    public static ChannelAdminView from(Channel c) {
        return new ChannelAdminView(
                c.id(), c.type().code(), c.name(), c.status().code(), c.weight(), c.baseUrl(),
                c.models(), c.group(), c.priority(), c.autoBan(), c.balance(), c.usedQuota(),
                c.responseTime(), c.testTime(), c.modelMapping(), c.statusCodeMapping(), c.tag(),
                ChannelInfoView.from(c.channelInfo()), c.createdTime());
    }
}
