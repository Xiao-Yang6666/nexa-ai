package com.nexa.passkey.domain.vo;

import com.nexa.passkey.domain.exception.InvalidPasskeyCeremonyException;

import java.util.Objects;

/**
 * 凭据标识值对象（WebAuthn credential id，base64url 串）。
 *
 * <p>不可变、按值相等。WebAuthn authenticator 为每个注册产生全局唯一的 credential id，登录断言时
 * 据此定位用户的公钥。对齐 DB-SCHEMA §16 {@code credential_id varchar(512) unique not null}。</p>
 *
 * <p>不变量：非空、≤512（落库唯一索引列长上限）。本值对象不解码/不验签（验签属 ceremony 端口职责），
 * 仅守护标识本身的合法性。</p>
 */
public final class CredentialId {

    /** credential_id 最大长度，对齐 DB-SCHEMA §16 {@code varchar(512)}。 */
    public static final int MAX_LENGTH = 512;

    private final String value;

    private CredentialId(String value) {
        this.value = value;
    }

    /**
     * 构造凭据标识（校验非空 + 长度）。
     *
     * @param raw base64url 凭据标识串
     * @return 凭据标识值对象
     * @throws InvalidPasskeyCeremonyException 当为空或超长
     */
    public static CredentialId of(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidPasskeyCeremonyException("credential id must not be blank");
        }
        if (v.length() > MAX_LENGTH) {
            throw new InvalidPasskeyCeremonyException("credential id length must be <= " + MAX_LENGTH);
        }
        return new CredentialId(v);
    }

    /** @return base64url 凭据标识串 */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CredentialId other)) {
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
        return "CredentialId{" + value + "}";
    }
}
