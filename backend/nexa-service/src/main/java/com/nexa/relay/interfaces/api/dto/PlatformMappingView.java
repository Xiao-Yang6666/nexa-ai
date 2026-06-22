package com.nexa.relay.interfaces.api.dto;

import com.nexa.relay.domain.model.PlatformModelMapping;

/**
 * 超管底仓映射视图 DTO（仅 Admin/Root 路由返回，含 B）。
 *
 * <p>本视图含 {@code upstreamName}(B)——**只能用于 AdminAuth/RootAuth 路由**，客户路由绝不返回
 * （可见性铁律 ADR-COMPAT-05，数据层闸：PlatformModelMapping 无任何 user 路由读接口）。</p>
 */
public record PlatformMappingView(Long id, String publicName, String upstreamName, boolean enabled, String remark) {

    public static PlatformMappingView from(PlatformModelMapping m) {
        return new PlatformMappingView(m.id(), m.publicName(), m.upstreamName(), m.isEnabled(), m.remark());
    }
}
