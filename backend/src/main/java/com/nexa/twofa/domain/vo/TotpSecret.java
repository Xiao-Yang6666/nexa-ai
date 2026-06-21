package com.nexa.twofa.domain.vo;

import com.nexa.twofa.domain.exception.InvalidTwoFAStateException;

import java.security.SecureRandom;

/**
 * TOTP 共享密钥值对象（不可变，Base32 编码，F-1033）。
 *
 * <p>RFC 4648 Base32（无填充、大写 A-Z2-7）是 TOTP 生态（Google Authenticator / Authy 等）
 * 通用的密钥编码——otpauth URI 的 {@code secret} 参数即 Base32。本 VO 封装 Base32 串并保证：
 * <ul>
 *   <li>非空、长度受界（≤255，对齐 DB-SCHEMA §14 {@code secret varchar(255)}）。</li>
 *   <li>仅含合法 Base32 字符（构造即校验，非法立即抛 {@link InvalidTwoFAStateException}）。</li>
 *   <li>可解码为原始字节（供 {@link TotpVerifier} HMAC 计算）。</li>
 * </ul>
 * 敏感数据：{@code secret} 落库但<b>绝不</b>进客户视图（DB-SCHEMA §14 {@code json:"-"}）。
 * 不可变 + 按值相等（值对象，backend-engineer §2.4）。</p>
 */
public final class TotpSecret {

    /** Base32 字母表（RFC 4648，无填充）。 */
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /** 落库长度上限，对齐 DB-SCHEMA §14 {@code varchar(255)}。 */
    public static final int MAX_LENGTH = 255;

    /** 生成密钥默认字节数（160 bit，RFC 6238 §5.1 建议 ≥160 bit / HMAC-SHA1 块长）。 */
    private static final int DEFAULT_SECRET_BYTES = 20;

    private final String value;

    private TotpSecret(String value) {
        this.value = value;
    }

    /**
     * 从既有 Base32 串构造（校验合法性）。
     *
     * @param base32 Base32 编码的密钥串（大写，无填充 {@code =}）
     * @return 密钥值对象
     * @throws InvalidTwoFAStateException 空 / 超长 / 含非 Base32 字符
     */
    public static TotpSecret of(String base32) {
        if (base32 == null || base32.isBlank()) {
            throw new InvalidTwoFAStateException("totp secret must not be blank");
        }
        String normalized = base32.trim().toUpperCase();
        if (normalized.length() > MAX_LENGTH) {
            throw new InvalidTwoFAStateException("totp secret length must be <= " + MAX_LENGTH);
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (BASE32_ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                throw new InvalidTwoFAStateException("totp secret must be valid Base32");
            }
        }
        return new TotpSecret(normalized);
    }

    /**
     * 生成新的随机 TOTP 密钥（setup 第一步，F-1033）。
     *
     * <p>用 {@link SecureRandom} 生成 {@value #DEFAULT_SECRET_BYTES} 字节熵后 Base32 编码。
     * 安全默认：密码学安全随机源，避免可预测密钥（backend-engineer §3.4 安全）。</p>
     *
     * @return 新随机密钥值对象
     */
    public static TotpSecret generate() {
        byte[] buf = new byte[DEFAULT_SECRET_BYTES];
        new SecureRandom().nextBytes(buf);
        return new TotpSecret(encodeBase32(buf));
    }

    /**
     * 解码为原始密钥字节（供 HMAC 计算）。
     *
     * @return 原始密钥字节
     */
    public byte[] decode() {
        return decodeBase32(value);
    }

    /** @return Base32 编码串（落库值；敏感，绝不进客户视图） */
    public String value() {
        return value;
    }

    // ---- Base32 编解码（RFC 4648，无填充；自实现避免引第三方依赖） ----

    /**
     * 字节数组 → Base32 串（无填充）。
     *
     * @param data 原始字节
     * @return Base32 编码串
     */
    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            // 末尾不足 5 bit 时左移补零取高位（无填充编码）。
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Base32 串 → 字节数组（无填充，已假定输入仅含合法字符——构造时已校验）。
     *
     * @param base32 Base32 编码串（大写，无填充）
     * @return 原始字节
     */
    private static byte[] decodeBase32(String base32) {
        int buffer = 0;
        int bitsLeft = 0;
        // 5 bit/字符，输出字节数 = floor(len*5/8)。
        byte[] out = new byte[base32.length() * 5 / 8];
        int pos = 0;
        for (int i = 0; i < base32.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(base32.charAt(i));
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[pos++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TotpSecret other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
