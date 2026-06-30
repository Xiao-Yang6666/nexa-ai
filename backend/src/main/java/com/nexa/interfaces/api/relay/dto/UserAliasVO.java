package com.nexa.interfaces.api.relay.dto;

import com.nexa.domain.relay.model.UserModelAlias;

/**
 * 客户层别名视图 DTO（用户自助列表，只含 C→A，不涉及 B）。
 */
public record UserAliasVO(Long id, String alias, String target, boolean enabled) {

    public static UserAliasVO from(UserModelAlias a) {
        return new UserAliasVO(a.id(), a.alias(), a.target(), a.isEnabled());
    }
}
