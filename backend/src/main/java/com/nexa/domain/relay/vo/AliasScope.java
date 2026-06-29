package com.nexa.domain.relay.vo;

import com.nexa.domain.relay.exception.InvalidRelayParameterException;

import java.util.Objects;

/**
 * 别名作用域值对象（UserModelAlias 的 scope_type + scope_id，COMPAT-LAYER-ARCHITECTURE §4.2）。
 *
 * <p>领域规则：
 * <ul>
 *   <li>{@code type} 枚举 user/group；</li>
 *   <li>{@code id}：user→user_id 字符串化；group→分组名（对齐 User.Group/Token.Group varchar(64)）；</li>
 *   <li>优先级：解析时 user 级 > group 级（同一 C 命中多 scope 时取 user）。</li>
 * </ul>
 * 不可变、按值相等（值对象本质）。</p>
 *
 * @param type 作用域类型
 * @param id   作用域标识
 */
public record AliasScope(ScopeType type, String id) {

    /** 作用域类型枚举。 */
    public enum ScopeType {
        USER("user"),
        GROUP("group");

        private final String wire;
        ScopeType(String wire) { this.wire = wire; }
        public String wire() { return wire; }

        public static ScopeType fromWire(String wire) {
            for (ScopeType t : values()) {
                if (t.wire.equalsIgnoreCase(wire)) return t;
            }
            throw new InvalidRelayParameterException("invalid scope_type: " + wire);
        }
    }

    public AliasScope {
        Objects.requireNonNull(type, "scope type must not be null");
        if (id == null || id.isBlank()) {
            throw new InvalidRelayParameterException("scope_id must not be blank");
        }
        if (id.length() > UserModelAliasScopeLimits.SCOPE_ID_MAX_LENGTH) {
            throw new InvalidRelayParameterException("scope_id exceeds max length");
        }
    }

    /** user 级作用域工厂。 */
    public static AliasScope user(long userId) {
        return new AliasScope(ScopeType.USER, String.valueOf(userId));
    }

    /** group 级作用域工厂。 */
    public static AliasScope group(String groupName) {
        return new AliasScope(ScopeType.GROUP, groupName);
    }

    /** 是否 user 级（解析优先级判定用）。 */
    public boolean isUserScope() {
        return type == ScopeType.USER;
    }

    /** 内部常量持有（避免与聚合根常量循环依赖）。 */
    private static final class UserModelAliasScopeLimits {
        static final int SCOPE_ID_MAX_LENGTH = 64;
    }
}
