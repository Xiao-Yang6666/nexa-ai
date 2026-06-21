package com.nexa.account.domain.vo;

import com.nexa.account.domain.exception.InvalidCredentialException;

import java.security.SecureRandom;

/**
 * 邮箱验证码值对象。
 *
 * <p>不可变、按值相等。承载 F-1004 发送、F-1005 校验流程中的数字验证码。
 * 领域规则来源：PRD AC-1 R4~R7「请求发送验证码 → 校验验证码匹配且未过期」、
 * API-ENDPOINTS §1.1「EmailVerificationEnabled=true 时 verification_code 必填」。</p>
 *
 * <p>码长固定 {@link #LENGTH} 位纯数字（邮件可读、便于手输）。本值对象只表达「一个合法形态的验证码」，
 * 其「是否匹配某邮箱、是否过期」属带状态/IO 的判定，由应用层端口
 * {@code VerificationCodeService} 负责，不进值对象（保持 domain 纯净可单测）。</p>
 */
public final class VerificationCode {

    /** 验证码位数（6 位数字，行业惯例 + 邮件可读）。 */
    public static final int LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    private VerificationCode(String value) {
        this.value = value;
    }

    /**
     * 工厂方法：校验并构造验证码值对象（用于校验入参）。
     *
     * @param raw 原始验证码字符串（可能含首尾空白）
     * @return 规范化（trim）后的验证码值对象
     * @throws InvalidCredentialException 当验证码为空、长度不符或含非数字字符时
     */
    public static VerificationCode of(String raw) {
        String normalized = raw == null ? null : raw.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidCredentialException("verification code must not be blank");
        }
        if (normalized.length() != LENGTH || !normalized.chars().allMatch(Character::isDigit)) {
            throw new InvalidCredentialException(
                    "verification code must be " + LENGTH + " digits");
        }
        return new VerificationCode(normalized);
    }

    /**
     * 生成一枚新的随机验证码（用于发码流程 F-1004）。
     *
     * <p>用 {@link SecureRandom} 而非普通随机，避免验证码可预测被绕过（安全默认）。
     * 左补零保证恒为 {@link #LENGTH} 位。</p>
     *
     * @return 新生成的随机验证码值对象
     */
    public static VerificationCode generate() {
        int bound = (int) Math.pow(10, LENGTH); // 6 位 → [0, 1_000_000)
        int n = RANDOM.nextInt(bound);
        return new VerificationCode(String.format("%0" + LENGTH + "d", n));
    }

    /** @return 验证码字符串（{@link #LENGTH} 位数字） */
    public String value() {
        return value;
    }

    /**
     * 常量时间比较，判断是否与另一验证码相等。
     *
     * <p>用按位异或累加做定长比较，避免 {@link String#equals} 的短路特性泄露「前缀匹配长度」，
     * 削弱针对验证码的时序侧信道（安全默认）。</p>
     *
     * @param other 待比较的验证码（可为 null）
     * @return 完全相等返回 {@code true}
     */
    public boolean matches(VerificationCode other) {
        if (other == null) {
            return false;
        }
        byte[] a = this.value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = other.value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VerificationCode other)) {
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
