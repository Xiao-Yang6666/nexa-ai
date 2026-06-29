package com.nexa.interfaces.model.api.dto;

import java.util.List;

/** 对外模型列表包裹（items + total）。 */
public record PublicModelListVO(List<PublicModelAdminVO> items, long total) {}
