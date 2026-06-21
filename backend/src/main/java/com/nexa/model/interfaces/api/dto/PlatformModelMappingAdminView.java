package com.nexa.model.interfaces.api.dto;

import com.nexa.model.domain.model.PlatformModelMapping;

/**
 * 超管底仓映射管理视图（接口层出参，AdminView，F-6002）。
 *
 * <p>对齐 openapi PlatformModelMappingAdminView。含 upstream_name=B，仅 admin/root，
 * 客户绝不可见（B 不可见序列化层闸）。</p>
 */
public record PlatformModelMappingAdminView(
        Long id,
        String publicName,
        String upstreamName,
        Boolean enabled,
        String remark,
        Long createdTime,
        Long updatedTime
) {
    /** 由领域聚合裁剪为管理视图。 */
    public static PlatformModelMappingAdminView from(PlatformModelMapping m) {
        return new PlatformModelMappingAdminView(m.id(), m.publicName(), m.upstreamName(),
                m.enabled(), m.remark(), m.createdTime(), m.updatedTime());
    }
}
