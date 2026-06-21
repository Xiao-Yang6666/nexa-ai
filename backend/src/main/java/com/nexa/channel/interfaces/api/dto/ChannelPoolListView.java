package com.nexa.channel.interfaces.api.dto;

import java.util.List;

/** 渠道池成员列表包裹（items，无 total——渠道池小规模不分页）。 */
public record ChannelPoolListView(List<ChannelPoolMember> items) {}
