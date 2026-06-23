package com.nexa.channel.application;

import com.nexa.channel.domain.vo.ChannelInfo;

/**
 * 创建渠道命令（应用层入参 DTO，F-2016 create，含 F-2020/F-2021/F-2022/F-2025）。
 *
 * <p>承载创建渠道所需的全部入参（对齐 openapi ChannelCreateRequest）。应用层用例据此调用聚合工厂
 * {@link com.nexa.channel.domain.model.Channel#create}，字段校验/归一在聚合内（充血）。
 * param_override/header_override 作为附加设置随 setting 透传（F-2025 覆写在 setting JSON 内承载）。</p>
 *
 * @param type              渠道 type（必填）
 * @param key               上游凭证（必填，敏感）
 * @param models            支持模型集（必填，逗号分隔）
 * @param name              渠道名（可空）
 * @param group             分组（可空→default）
 * @param priority          优先级
 * @param weight            权重
 * @param autoBan           自动禁用开关
 * @param baseUrl           上游 BaseURL（可空）
 * @param modelMapping      模型映射 JSON（F-2021，可空）
 * @param statusCodeMapping 状态码映射 JSON（F-2022，≤1024，可空）
 * @param setting           附加设置 JSON（含 F-2025 param/header 覆写，可空）
 * @param tag               标签（可空）
 * @param channelInfo       多 Key 信息（F-2020，可空→单 Key）
 */
public record CreateChannelCommand(Integer type, String key, String models, String name, String group,
                                   Long priority, Integer weight, Integer autoBan, String baseUrl,
                                   String modelMapping, String statusCodeMapping, String setting,
                                   String tag, ChannelInfo channelInfo) {
}
