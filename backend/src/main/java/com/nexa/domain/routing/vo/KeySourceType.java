package com.nexa.domain.routing.vo;

/**
 * 会话键来源类型值对象（F-2029 会话亲和键提取，PRD CH-4 §2「key_sources」）。
 *
 * <p>领域规则来源：FC-068 {@code channel_affinity_setting.go}。亲和规则按 {@code key_sources} 从请求中
 * 提取会话键，键来源有四类（与现网一致）：
 * <ul>
 *   <li>{@link #GJSON} —— 从请求体 JSON 用 gjson 路径取值（如内置 codex 取 {@code prompt_cache_key}、
 *       claude 取 {@code metadata.user_id}）。</li>
 *   <li>{@link #REQUEST_HEADER} —— 从请求头取值。</li>
 *   <li>{@link #CONTEXT_INT} —— 从 relay 上下文取整型值。</li>
 *   <li>{@link #CONTEXT_STRING} —— 从 relay 上下文取字符串值。</li>
 * </ul>
 * 持久化为可读字符串，解析未知值抛由调用方决定（规则配置为关键路由配置，不静默吞错）。</p>
 */
public enum KeySourceType {

    /** 请求体 JSON gjson 路径。 */
    GJSON("gjson"),

    /** 请求头。 */
    REQUEST_HEADER("request_header"),

    /** relay 上下文整型。 */
    CONTEXT_INT("context_int"),

    /** relay 上下文字符串。 */
    CONTEXT_STRING("context_string");

    private final String wire;

    KeySourceType(String wire) {
        this.wire = wire;
    }

    /** @return 线上/持久化字符串表示 */
    public String wire() {
        return wire;
    }

    /**
     * 由字符串解析来源类型（大小写不敏感）。
     *
     * <p>null/未知值回退 {@link #GJSON}——gjson 为内置 codex/claude 规则的主用来源，
     * 宽容兼容旧配置，避免单个来源拼写错误使整条规则不可用。</p>
     *
     * @param raw 原始字符串
     * @return 对应来源类型（缺省 GJSON）
     */
    public static KeySourceType fromWire(String raw) {
        if (raw == null) {
            return GJSON;
        }
        String v = raw.trim().toLowerCase();
        for (KeySourceType t : values()) {
            if (t.wire.equals(v)) {
                return t;
            }
        }
        return GJSON;
    }
}
