package com.nexa.prefill.domain.vo;

import com.nexa.prefill.domain.exception.InvalidPrefillParameterException;

/**
 * 预填分组类型值对象（DB-SCHEMA §17 PrefillGroup.type 枚举 {@code model/tag/endpoint}）。
 *
 * <p>预填分组按类型承载三类下拉预填配置（PRD 模块十五 §14）：
 * <ul>
 *   <li>{@link #MODEL}    —— 模型组（一批模型名，渠道/令牌配置时下拉预填）；</li>
 *   <li>{@link #TAG}      —— 标签组（一批标签）；</li>
 *   <li>{@link #ENDPOINT} —— 端点组（一批端点/入站协议）。</li>
 * </ul>
 * 名称冲突校验按本类型维度进行（同 type 下 name 唯一，{@link com.nexa.prefill.domain.exception.PrefillGroupNameConflictException}）。
 * 落库为小写字符串（{@link #wireValue()}），与 openapi enum 字面量一致。</p>
 */
public enum PrefillType {

    /** 模型组。 */
    MODEL("model"),

    /** 标签组。 */
    TAG("tag"),

    /** 端点组。 */
    ENDPOINT("endpoint");

    private final String wireValue;

    PrefillType(String wireValue) {
        this.wireValue = wireValue;
    }

    /** @return 线上/落库字面量（小写，对齐 openapi enum 与 DB type 列） */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 由字面量解析类型（API 入参/持久化重建方向）。
     *
     * <p>大小写不敏感、去首尾空白后匹配。非法值抛 {@link InvalidPrefillParameterException}
     * （对齐 openapi {@code GET} 的「type 非法枚举 → 400 invalid type」；不静默兜底为某默认值，
     * 避免脏 type 流入查询/落库，backend-engineer §3.2 不吞错）。</p>
     *
     * @param raw 原始字面量（如 {@code "model"}）
     * @return 对应类型
     * @throws InvalidPrefillParameterException 当 raw 为空或非合法枚举时
     */
    public static PrefillType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPrefillParameterException("prefill type is required");
        }
        String normalized = raw.trim().toLowerCase();
        for (PrefillType t : values()) {
            if (t.wireValue.equals(normalized)) {
                return t;
            }
        }
        throw new InvalidPrefillParameterException("invalid type: " + raw);
    }
}
