package com.nexa.shared.security.validation;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全标识符值对象（防 SQL 注入：动态 SQL 片段白名单）。
 *
 * <p>不可变、按值相等。用于<b>无法参数化</b>的场景——SQL 的列名/排序方向等结构性片段不能用
 * {@code ?} 占位符绑定（JDBC 参数只能绑值不能绑标识符）。这正是 SQL 注入最常被忽略的入口：
 * 把前端传来的 {@code sort=col} / {@code order=ASC} 直接拼进 ORDER BY。</p>
 *
 * <p>防注入策略（双保险）：
 * <ol>
 *   <li><b>白名单</b>：调用方传入本字段在该查询里<b>允许</b>的标识符集合，不在白名单一律拒绝；</li>
 *   <li><b>字符约束</b>：即便在白名单内，也强制只含 {@code [A-Za-z0-9_]}，杜绝引号/分号/注释符。</li>
 * </ol>
 * 业务数据的值过滤一律走 JPA 参数化（{@code :param} / 方法名派生查询），天然防注入，不经本 VO。</p>
 *
 * <p>设计依据：backend-engineer §3.4「输入校验在接口层做、不信任外部输入」+ 本切片 SECURITY-NOTES
 * 「防 SQL 注入」。</p>
 */
public final class SafeIdentifier {

    /** 合法标识符字符集（保守白名单：字母数字下划线，禁一切引号/分号/空白/注释符）。 */
    private static final Pattern SAFE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private final String value;

    private SafeIdentifier(String value) {
        this.value = value;
    }

    /**
     * 校验并构造安全标识符：必须同时满足「在调用方给定白名单内」与「字符集合法」。
     *
     * @param raw       外部传入的原始标识符（如 query 参数 sort 列名）
     * @param allowList 该查询位置允许的标识符全集（不可为空；空集等于全部拒绝）
     * @return 安全标识符值对象
     * @throws IllegalArgumentException 当 raw 为空、不在白名单、或含非法字符
     */
    public static SafeIdentifier of(String raw, Set<String> allowList) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        // 先做字符集硬约束（即便白名单写错也兜底拦住注入字符）。
        if (!SAFE_PATTERN.matcher(raw).matches()) {
            throw new IllegalArgumentException("identifier contains illegal characters: " + raw);
        }
        if (allowList == null || !allowList.contains(raw)) {
            // 不回显白名单内容，避免泄露内部列结构。
            throw new IllegalArgumentException("identifier is not allowed: " + raw);
        }
        return new SafeIdentifier(raw);
    }

    /** @return 已校验、可安全拼入 SQL 结构片段的标识符字符串 */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SafeIdentifier other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
