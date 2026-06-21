package com.nexa.model.interfaces.api.dto;

import java.util.List;

/** 底仓映射列表包裹（items + total）。 */
public record PlatformModelMappingListView(List<PlatformModelMappingAdminView> items, long total) {}
