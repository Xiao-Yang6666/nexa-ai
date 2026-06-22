package com.nexa.account.domain.vo;

import com.nexa.account.domain.exception.InvalidCredentialException;

import java.util.regex.Pattern;

/**
 * 邮箱值对象。
 *
 * <p>不可变、按值相等（大小写不敏感，统一小写存储）。</p>
 *
 * <p>约束来源：openapi.yaml F-1001 register.email（{@code maxLength: 50}）
 * + DB-SCHEMA §1 {@code email @Column(length = 50)}。注册时 email 为可选字段，
 * 因此本值对象仅在调用方提供非空邮箱时构造。</p>
 */
public final class Email {

    /** 邮箱最大长度，对齐 DB-SCHEMA §1 与 openapi register schema。 */
    public static final int MAX_LENGTH = 50;

    // 轻量邮箱格式校验：领域只保证"看起来是邮箱"，发信可达性由发码流程(F-1004/F-1005)兜。
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造邮箱值对象。
     *
     * @param raw 原始邮箱字符串
     * @return 规范化（去空白、转小写）后的邮箱值对象
     * @throws InvalidCredentialException 当邮箱为空、超长或格式非法时
     */
    public static Email of(String raw) {
        String normalized = raw == null ? null : raw.trim().toLowerCase();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidCredentialException("email must not be blank");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "email length must be <= " + MAX_LENGTH + ", got " + normalized.length());
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidCredentialException("email format is invalid: " + normalized);
        }
        return new Email(normalized);
    }

    /** @return 规范化后的邮箱字符串（小写） */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Email other)) {
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
