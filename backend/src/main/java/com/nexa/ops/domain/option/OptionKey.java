package com.nexa.ops.domain.option;

import java.util.Objects;

/**
 * 配置键（值对象，不可变，按值相等）。
 *
 * <p>封装全站选项键的领域语义，核心职责是「敏感键判定」——F-4017 全站选项列表查询时，
 * 凡以 {@code Token}/{@code Secret}/{@code Key}（区分大小写）或 {@code secret}/{@code api_key}
 * （小写）结尾的键，其值不得下发给客户视图（即便对 root）。把该判定收敛到值对象上，避免
 * 散落在 controller/service 里的字符串后缀比较（充血，backend-engineer §2.2）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §9.2 GET /api/option/「自动剔除以 Token/Secret/Key/secret/api_key
 * 结尾的键」。注意大小写敏感：{@code Token}（首字母大写）命中、{@code token} 不命中，与现网
 * Go 版 {@code strings.HasSuffix} 行为一致（键名约定为驼峰，敏感后缀首字母大写）。</p>
 */
public final class OptionKey {

    /** 敏感后缀（大小写敏感）：以这些结尾的键其值视为机密，列表查询时剔除。 */
    private static final String[] SENSITIVE_SUFFIXES = {"Token", "Secret", "Key", "secret", "api_key"};

    private final String value;

    private OptionKey(String value) {
        this.value = value;
    }

    /**
     * 由键字符串构造（去除首尾空白）。
     *
     * @param raw 键名（非空、非空白）
     * @return 配置键值对象
     * @throws IllegalArgumentException 键为 null/空白（脏键不进领域）
     */
    public static OptionKey of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("option key must not be null or blank");
        }
        return new OptionKey(raw.trim());
    }

    /** @return 键名原文 */
    public String value() {
        return value;
    }

    /**
     * 本键是否为「敏感键」（值不得进客户视图列表）。
     *
     * <p>判定规则：键名以任一敏感后缀（{@link #SENSITIVE_SUFFIXES}）结尾即为敏感。大小写敏感，
     * 与现网 {@code strings.HasSuffix} 一致。F-4017 列表查询据此剔除。</p>
     *
     * @return 敏感返回 {@code true}
     */
    public boolean isSensitive() {
        for (String suffix : SENSITIVE_SUFFIXES) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OptionKey other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
