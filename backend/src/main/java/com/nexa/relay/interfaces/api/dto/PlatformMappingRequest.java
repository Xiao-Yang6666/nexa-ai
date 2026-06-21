package com.nexa.relay.interfaces.api.dto;

/**
 * 超管底仓映射创建/编辑请求 DTO（L2 A→B，Admin/Root，F-6011）。
 *
 * @param publicName   A 平台公开名
 * @param upstreamName B 真实上游名（仅 admin/root 可写）
 * @param remark       备注（可空）
 */
public record PlatformMappingRequest(String publicName, String upstreamName, String remark) {
}
