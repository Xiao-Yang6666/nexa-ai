package com.nexa.domain.account.vo;

import com.nexa.domain.account.exception.InvalidCredentialException;

/**
 * 用户名值对象。
 *
 * <p>不可变、按值相等。封装注册契约对用户名的约束，使非法用户名无法进入领域。</p>
 *
 * <p>约束来源：openapi.yaml F-1001 register.username（{@code maxLength: 20}）
 * + DB-SCHEMA §1 {@code username @Column(length = 20)}。用户名非空且不超过 20 字符。</p>
 */
public final class Username {

    /** 用户名最大长度，对齐 DB-SCHEMA §1 与 openapi register schema。 */
    public static final int MAX_LENGTH = 20;

    private final String value;

    private Username(String value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造用户名值对象。
     *
     * @param raw 原始用户名（可能含首尾空白）
     * @return 规范化后的用户名值对象
     * @throws InvalidCredentialException 当用户名为空或超过 {@link #MAX_LENGTH} 时
     */
    public static Username of(String raw) {
        // 在领域边界做规范化（trim），避免" foo "与"foo"被当成不同用户。
        String normalized = raw == null ? null : raw.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidCredentialException("username must not be blank");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "username length must be <= " + MAX_LENGTH + ", got " + normalized.length());
        }
        return new Username(normalized);
    }

    /** @return 规范化后的用户名字符串 */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Username other)) {
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
