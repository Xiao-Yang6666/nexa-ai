package com.nexa.channel.interfaces.api.dto;

import java.util.List;

/** 成本倍率列表包裹（items + total）。 */
public record ChannelModelCostListView(List<ChannelModelCostAdminView> items, long total) {}
