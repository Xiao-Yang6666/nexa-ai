package com.nexa.interfaces.model.api.dto;

import com.nexa.domain.model.model.Vendor;

/**
 * 供应商元数据管理视图（接口层出参，AdminView，F-3018）。
 *
 * <p>对齐 openapi {@code VendorAdminVO}。供应商仅承载名称/图标/状态，无敏感信息。时间戳不在
 * openapi VendorAdminVO 中，故本视图按契约只暴露 id/name/icon/status。</p>
 *
 * @param id     主键
 * @param name   供应商名
 * @param icon   图标
 * @param status 状态码
 */
public record VendorAdminVO(Long id, String name, String icon, int status) {

    /**
     * 由领域聚合裁剪为管理视图。
     *
     * @param v 供应商聚合
     * @return 管理视图
     */
    public static VendorAdminVO from(Vendor v) {
        return new VendorAdminVO(v.id(), v.name(), v.icon(), v.status().code());
    }
}
