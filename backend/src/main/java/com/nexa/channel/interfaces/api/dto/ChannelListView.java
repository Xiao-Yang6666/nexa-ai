package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.application.ChannelPage;

import java.util.List;

/**
 * 渠道列表/搜索响应视图 DTO（接口层，对齐 openapi 列表出参 {@code { items, total }}，F-2016）。
 *
 * <p>将应用层 {@link ChannelPage}（领域聚合 + total）裁剪为管理视图列表（每项剔除 key 等敏感字段）。</p>
 *
 * @param items 当前页渠道管理视图列表
 * @param total 满足条件的总条数
 */
public record ChannelListView(
        @JsonProperty("items") List<ChannelAdminView> items,
        @JsonProperty("total") long total) {

    /**
     * 由应用层分页结果组装列表视图。
     *
     * @param page 渠道分页结果
     * @return 列表视图 DTO
     */
    public static ChannelListView from(ChannelPage page) {
        return new ChannelListView(
                page.items().stream().map(ChannelAdminView::from).toList(),
                page.total());
    }
}
