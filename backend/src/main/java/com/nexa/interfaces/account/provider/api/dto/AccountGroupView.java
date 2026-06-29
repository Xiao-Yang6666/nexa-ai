package com.nexa.interfaces.account.provider.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.domain.account.provider.vo.AccountGroupRef;

/**
 * 账号-分组关联请求/视图 DTO（接口层）。
 *
 * @param group    分组（字符串，对齐 channel/abilities 的 group）
 * @param priority 组内优先级（可空→50）
 */
public record AccountGroupView(
        @JsonProperty("group") String group,
        @JsonProperty("priority") Integer priority) {

    /**
     * 转领域值对象（priority 归一在值对象工厂）。
     *
     * @return 关联引用值对象
     */
    public AccountGroupRef toDomain() {
        return AccountGroupRef.of(group, priority);
    }

    /**
     * 由领域值对象组装视图。
     *
     * @param ref 关联引用
     * @return 视图 DTO
     */
    public static AccountGroupView from(AccountGroupRef ref) {
        return new AccountGroupView(ref.group(), ref.priority());
    }
}
