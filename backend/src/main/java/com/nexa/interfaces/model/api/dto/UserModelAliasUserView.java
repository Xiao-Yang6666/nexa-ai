package com.nexa.interfaces.model.api.dto;

import com.nexa.domain.model.model.UserModelAlias;

/**
 * 客户层自助映射用户视图（接口层出参，UserView，F-6003）。
 *
 * <p>对齐 openapi UserModelAliasUserView。target 仅 A，绝不含 B（B 不可见序列化层闸）。</p>
 */
public record UserModelAliasUserView(
        Long id,
        String scopeType,
        String scopeId,
        String alias,
        String target,
        Boolean enabled,
        Long createdTime
) {
    /** 由领域聚合裁剪为用户视图。 */
    public static UserModelAliasUserView from(UserModelAlias a) {
        return new UserModelAliasUserView(a.id(), a.scopeType().code(), a.scopeId(),
                a.alias(), a.target(), a.enabled(), a.createdTime());
    }
}
