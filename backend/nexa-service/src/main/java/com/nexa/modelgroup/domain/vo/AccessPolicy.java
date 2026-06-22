package com.nexa.modelgroup.domain.vo;

import com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException;

/**
 * 模型组访问策略值对象。
 *
 * <p>决定一个模型组对终端用户/令牌的可见与可用范围，是「灵活分组」的核心——分组不再与用户等级强绑定，
 * 而是由模型组自身声明开放方式：
 * <ul>
 *   <li>{@link #PUBLIC}     公开：所有用户/令牌均可访问（无需显式授权）；</li>
 *   <li>{@link #PRIVATE}    私有：仅被显式授权（{@code model_group_access}）的用户/令牌可访问；</li>
 *   <li>{@link #AUTO_LEVEL} 自动：按用户角色等级自动映射（由 KV 配置 {@code level→code} 决定可见性，
 *       管理员无需逐用户授权即可让某等级用户自动获得某组）。</li>
 * </ul>
 * 落库为大写字面量（{@link #wireValue()}），与 API enum 一致。</p>
 */
public enum AccessPolicy {

    /** 公开：所有用户/令牌可访问。 */
    PUBLIC("PUBLIC"),

    /** 私有：仅显式授权可访问。 */
    PRIVATE("PRIVATE"),

    /** 自动：按用户等级自动映射。 */
    AUTO_LEVEL("AUTO_LEVEL");

    private final String wireValue;

    AccessPolicy(String wireValue) {
        this.wireValue = wireValue;
    }

    /** @return 落库/线上字面量（大写） */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 由字面量解析访问策略（API 入参 / 持久化重建方向）。
     *
     * <p>大小写不敏感、去首尾空白后匹配。非法值抛 {@link InvalidModelGroupParameterException}
     * （不静默兜底，避免脏值流入查询/落库，backend-engineer §3.2 不吞错）。</p>
     *
     * @param raw 原始字面量（如 {@code "public"}）
     * @return 对应策略
     * @throws InvalidModelGroupParameterException 当 raw 为空或非合法枚举时
     */
    public static AccessPolicy fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidModelGroupParameterException("access policy is required");
        }
        String normalized = raw.trim().toUpperCase();
        for (AccessPolicy p : values()) {
            if (p.wireValue.equals(normalized)) {
                return p;
            }
        }
        throw new InvalidModelGroupParameterException("invalid access policy: " + raw);
    }
}
