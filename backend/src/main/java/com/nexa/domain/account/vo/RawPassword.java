package com.nexa.domain.account.vo;

import com.nexa.domain.account.exception.InvalidCredentialException;

/**
 * 明文密码值对象。
 *
 * <p>不可变。封装注册/改密时对明文密码的长度约束，使非法密码无法进入领域。
 * <b>本对象只持有明文且仅用于即时哈希</b>，绝不落库、绝不进入任何 DTO/日志。</p>
 *
 * <p>约束来源：openapi.yaml F-1001 register.password（{@code minLength: 8, maxLength: 20}）
 * + API-ENDPOINTS §1.1（{@code validate:min=8,max=20}）+ DB-SCHEMA §1
 * （{@code password @Column(length = 20)} 指落库的是哈希语义字段，明文长度上限沿用契约 20）。</p>
 */
public final class RawPassword {

    /** 明文密码最小长度，对齐 openapi register schema 与 API-ENDPOINTS。 */
    public static final int MIN_LENGTH = 8;

    /** 明文密码最大长度，对齐 openapi register schema 与 API-ENDPOINTS。 */
    public static final int MAX_LENGTH = 20;

    private final String value;

    private RawPassword(String value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造明文密码值对象。
     *
     * <p>不对密码做 trim——前后空白对密码是有意义字符，trim 会悄悄改变用户密码。</p>
     *
     * @param raw 原始明文密码
     * @return 明文密码值对象
     * @throws InvalidCredentialException 当密码为空、过短或过长时
     */
    public static RawPassword of(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidCredentialException("password must not be blank");
        }
        if (raw.length() < MIN_LENGTH) {
            throw new InvalidCredentialException(
                    "password length must be >= " + MIN_LENGTH);
        }
        if (raw.length() > MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "password length must be <= " + MAX_LENGTH);
        }
        return new RawPassword(raw);
    }

    /**
     * 暴露明文，<b>仅供基础设施层哈希器即时计算哈希用</b>。调用方不得持久化或记录该值。
     *
     * @return 明文密码字符串
     */
    public String value() {
        return value;
    }
}
