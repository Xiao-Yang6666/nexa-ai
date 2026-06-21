package com.nexa.channel.application;

import com.nexa.channel.domain.vo.ChannelInfo;

/**
 * 编辑渠道命令（应用层入参 DTO，F-2016 update，含 F-2020/F-2021/F-2022/F-2025）。
 *
 * <p>承载编辑渠道所需的全部入参（对齐 openapi ChannelUpdateRequest = ChannelCreateRequest + id）。
 * key 为可选更新（null/空白=保留原 key），其余覆盖式。校验/归一在聚合 {@code update} 内（充血）。</p>
 *
 * @param id                渠道 id（必填）
 * @param type              渠道 type（必填）
 * @param key               新上游凭证（null/空白=保留原 key）
 * @param models            支持模型集（必填）
 * @param name              渠道名（可空）
 * @param group             分组（可空→default）
 * @param priority          优先级
 * @param weight            权重
 * @param autoBan           自动禁用开关
 * @param baseUrl           上游 BaseURL（可空）
 * @param modelMapping      模型映射 JSON（可空）
 * @param statusCodeMapping 状态码映射 JSON（≤1024，可空）
 * @param setting           附加设置 JSON（含覆写，可空）
 * @param tag               标签（可空）
 * @param channelInfo       多 Key 信息（可空→单 Key）
 */
public record UpdateChannelCommand(Long id, Integer type, String key, String models, String name, String group,
                                   Long priority, Integer weight, Integer autoBan, String baseUrl,
                                   String modelMapping, String statusCodeMapping, String setting,
                                   String tag, ChannelInfo channelInfo) {
}
