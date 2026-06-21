package com.nexa.twofa.domain.vo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * TOTP 校验领域服务（RFC 6238 / RFC 4226，纯算法，零框架依赖，F-1033/1036）。
 *
 * <p>实现基于时间的一次性口令（TOTP = HOTP with time-based counter）：
 * <ul>
 *   <li>时间步长 {@value #TIME_STEP_SECONDS}s、{@value #DIGITS} 位、HMAC-SHA1（生态默认，
 *       Google Authenticator / Authy 均用此参数）。</li>
 *   <li>校验时检查当前及前后各 {@value #VERIFY_WINDOW} 个时间步（容忍客户端/服务端时钟漂移，
 *       RFC 6238 §5.2 建议的 resynchronization 窗口）。</li>
 * </ul>
 *
 * <p>领域服务而非聚合方法：TOTP 计算是跨"密钥 + 当前时间"的无状态纯函数，不属于任何单一聚合状态
 * （backend-engineer §2.4 领域服务）。设计为静态纯方法，便于单测（给定密钥+时间戳，输出确定）。
 * 不 import 任何框架，仅用 JDK {@code javax.crypto}。</p>
 */
public final class TotpVerifier {

    /** 时间步长（秒），RFC 6238 默认 30s。 */
    public static final int TIME_STEP_SECONDS = 30;

    /** 口令位数，生态默认 6 位。 */
    public static final int DIGITS = 6;

    /** 校验容忍窗口（前后各 N 个时间步），容忍时钟漂移。 */
    public static final int VERIFY_WINDOW = 1;

    /** 10 的 {@link #DIGITS} 次幂，用于截断取模。 */
    private static final int MODULO = 1_000_000;

    private TotpVerifier() {
        // 纯静态工具，不实例化。
    }

    /**
     * 校验用户提交的 TOTP 是否在容忍窗口内有效（F-1033 enable 第二步 / F-1036 登录第二步）。
     *
     * <p>遍历 [-{@value #VERIFY_WINDOW}, +{@value #VERIFY_WINDOW}] 时间步逐一比对生成口令。
     * 任一步命中即通过。比对用<b>定长字符串比较</b>避免提前短路（弱化时序侧信道，安全默认）。</p>
     *
     * @param secret      TOTP 共享密钥
     * @param code        用户提交的口令（已是数字串；非 6 位数字直接判否）
     * @param epochSecond 当前时间（epoch 秒，调用方注入便于单测）
     * @return 命中容忍窗口内任一口令返回 {@code true}，否则 {@code false}
     */
    public static boolean verify(TotpSecret secret, String code, long epochSecond) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.length() != DIGITS || !trimmed.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = secret.decode();
        long counter = epochSecond / TIME_STEP_SECONDS;
        for (int offset = -VERIFY_WINDOW; offset <= VERIFY_WINDOW; offset++) {
            String candidate = generate(key, counter + offset);
            if (constantTimeEquals(candidate, trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成指定时间点的 TOTP（供单测与校验复用）。
     *
     * @param secret      TOTP 共享密钥
     * @param epochSecond 时间（epoch 秒）
     * @return 6 位数字口令（左侧补零）
     */
    public static String generate(TotpSecret secret, long epochSecond) {
        return generate(secret.decode(), epochSecond / TIME_STEP_SECONDS);
    }

    /**
     * HOTP 核心：对给定计数器用 HMAC-SHA1 + 动态截断（RFC 4226 §5.3）生成口令。
     *
     * @param key     原始密钥字节
     * @param counter 时间步计数器
     * @return 6 位数字口令（左侧补零）
     */
    private static String generate(byte[] key, long counter) {
        try {
            byte[] msg = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            // RFC 4226 §5.4 动态截断：低 4 bit 作偏移，取连续 4 字节并清最高位。
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % MODULO;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            // HmacSHA1 是 JDK 标配算法，理论不会缺失；缺失即环境异常，wrap 上抛不吞错（§3.2）。
            throw new IllegalStateException("HmacSHA1 unavailable: TOTP cannot be computed", e);
        }
    }

    /**
     * 定长比较（不因首个不同字符提前返回），弱化时序侧信道。
     *
     * @param a 候选口令
     * @param b 用户提交口令
     * @return 完全相等返回 {@code true}
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
