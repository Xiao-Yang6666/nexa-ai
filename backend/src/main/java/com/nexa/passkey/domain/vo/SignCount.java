package com.nexa.passkey.domain.vo;

import com.nexa.passkey.domain.exception.InvalidPasskeyCeremonyException;

/**
 * 签名计数器值对象（WebAuthn signature counter，单调递增）。
 *
 * <p>不可变、按值相等。WebAuthn authenticator 每次成功断言会上报一个递增的签名计数器，relying party
 * 据此检测<b>克隆/重放</b>：若新计数器 ≤ 已存计数器（且二者均非 0），说明可能存在被克隆的认证器
 * （DB-SCHEMA §16 {@code cloneWarning}）。对齐 {@code sign_count bigint default 0}（原 uint32）。</p>
 *
 * <p>领域规则出处：WebAuthn L2 §6.1.1 Signature Counter Considerations——计数器回退视为克隆告警。</p>
 */
public final class SignCount {

    /** 初始计数器（注册后、首次断言前）。 */
    public static final SignCount ZERO = new SignCount(0L);

    private final long value;

    private SignCount(long value) {
        this.value = value;
    }

    /**
     * 构造签名计数器（非负）。
     *
     * @param raw 计数器值（authenticator 上报，uint32 落在 long 内）
     * @return 计数器值对象
     * @throws InvalidPasskeyCeremonyException 当为负
     */
    public static SignCount of(long raw) {
        if (raw < 0) {
            throw new InvalidPasskeyCeremonyException("sign count must be >= 0");
        }
        return new SignCount(raw);
    }

    /**
     * 判断给定的新计数器相对本计数器是否构成克隆告警。
     *
     * <p>领域规则：二者均非 0 且新值 ≤ 旧值 → 告警（计数器本应严格递增）。任一为 0（部分 authenticator
     * 不实现计数器）时不告警，避免误报。</p>
     *
     * @param next authenticator 本次上报的新计数器
     * @return 构成克隆告警返回 {@code true}
     */
    public boolean isCloneWarning(SignCount next) {
        return this.value != 0 && next.value != 0 && next.value <= this.value;
    }

    /** @return 计数器原始值 */
    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SignCount other)) {
            return false;
        }
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "SignCount{" + value + "}";
    }
}
