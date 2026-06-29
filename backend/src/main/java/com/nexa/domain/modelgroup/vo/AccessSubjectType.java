package com.nexa.domain.modelgroup.vo;

import com.nexa.domain.modelgroup.exception.InvalidModelGroupParameterException;

/**
 * 访问授权主体类型值对象（用户级 / 令牌级授权）。
 *
 * <p>私有模型组的访问授权可挂在两种粒度上：
 * <ul>
 *   <li>{@link #USER}  用户级：该用户名下所有令牌都获得该模型组访问权；</li>
 *   <li>{@link #TOKEN} 令牌级：仅指定令牌获得访问权（更细粒度，便于按 key 售卖）。</li>
 * </ul>
 * 落库为大写字面量，与 API enum 一致。</p>
 */
public enum AccessSubjectType {

    /** 用户级授权。 */
    USER("USER"),

    /** 令牌级授权。 */
    TOKEN("TOKEN");

    private final String wireValue;

    AccessSubjectType(String wireValue) {
        this.wireValue = wireValue;
    }

    /** @return 落库/线上字面量（大写） */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 由字面量解析主体类型。
     *
     * @param raw 原始字面量（如 {@code "user"}）
     * @return 对应主体类型
     * @throws InvalidModelGroupParameterException 当 raw 为空或非合法枚举时
     */
    public static AccessSubjectType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidModelGroupParameterException("access subject type is required");
        }
        String normalized = raw.trim().toUpperCase();
        for (AccessSubjectType t : values()) {
            if (t.wireValue.equals(normalized)) {
                return t;
            }
        }
        throw new InvalidModelGroupParameterException("invalid access subject type: " + raw);
    }
}
