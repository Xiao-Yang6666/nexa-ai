package com.nexa.ops.domain.option;

/**
 * 全站配置项（KV 值对象，对齐 DB-SCHEMA §18 Option / 表 options）。
 *
 * <p>系统配置以 key→value 字符串对承载（OAuth 开关 / 主题 / 限流分组 / 敏感词 / 法务文案 等）。
 * 本对象是配置项的领域表示：键用 {@link OptionKey}（封装敏感键判定），值为字符串（可空）。
 * 不可变，覆盖式更新产生新对象。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §9.2（GET/PUT /api/option/）+ DB-SCHEMA §18。</p>
 *
 * @param key   配置键（封装敏感判定）
 * @param value 配置值（字符串/JSON/文案，可空）
 */
public record Option(OptionKey key, String value) {

    /**
     * 由键名 + 值构造。
     *
     * @param key   键名（非空白）
     * @param value 值（可空）
     * @return 配置项
     */
    public static Option of(String key, String value) {
        return new Option(OptionKey.of(key), value);
    }

    /**
     * 本配置项是否应从客户可见列表中剔除（F-4017 敏感键剔除）。
     *
     * @return 键为敏感键返回 {@code true}
     */
    public boolean isSensitive() {
        return key.isSensitive();
    }

    /** @return 键名原文（便捷访问） */
    public String keyName() {
        return key.value();
    }
}
