package com.nexa.model.interfaces.api.dto;

import java.util.List;

/** 对外模型列表包裹（items + total）。 */
public record PublicModelListView(List<PublicModelAdminView> items, long total) {}
