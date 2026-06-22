package com.nexa.channel.application;

import com.nexa.channel.domain.model.Channel;

import java.util.List;

/**
 * 渠道分页结果（应用层读模型，承载当前页渠道 + 总数，F-2016 列表/搜索）。
 *
 * <p>由列表/搜索用例返回，接口层据此组装 {@code { items, total }} 响应（对齐 openapi 列表出参）。
 * 持有领域聚合列表（接口层再裁剪为 AdminView，剔除 key 等敏感字段）。</p>
 *
 * @param items 当前页渠道聚合列表
 * @param total 满足条件的总条数
 */
public record ChannelPage(List<Channel> items, long total) {
}
