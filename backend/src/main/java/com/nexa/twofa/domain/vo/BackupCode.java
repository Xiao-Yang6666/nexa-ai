package com.nexa.twofa.domain.vo;

import java.security.SecureRandom;

/**
 * 备份码生成领域服务（F-1035，纯算法值生成）。
 *
 * <p>备份码用于 authenticator 不可用时的应急登录（一次性，用后即焚）。本服务只负责<b>生成明文码</b>，
 * 哈希落库由 {@link BackupCodeHasher} 端口完成。生成参数：每次 {@value #BATCH_SIZE} 个，每码
 * {@value #CODE_DIGITS} 位数字（足够熵且便于手抄；用 {@link SecureRandom} 密码学安全随机）。</p>
 *
 * <p>纯静态工具：无状态，输出由随机源决定（不适合断言确定值，但可测格式/数量/字符集）。</p>
 */
public final class BackupCode {

    /** 单次生成的备份码数量（行业惯例 8~10 个）。 */
    public static final int BATCH_SIZE = 10;

    /** 单个备份码位数。 */
    public static final int CODE_DIGITS = 10;

    private BackupCode() {
        // 纯静态工具，不实例化。
    }

    /**
     * 生成一批明文备份码（F-1035）。
     *
     * <p>每码为 {@value #CODE_DIGITS} 位数字串（左侧补零）。明文仅本次返回用户一次，落库前由
     * {@link BackupCodeHasher} 哈希——服务端此后无法还原明文（遗失只能重置）。</p>
     *
     * @return {@value #BATCH_SIZE} 个明文备份码
     */
    public static String[] generateBatch() {
        SecureRandom random = new SecureRandom();
        String[] codes = new String[BATCH_SIZE];
        // 10 位十进制范围 [0, 10^10)；用 nextLong 取模到该范围，再补零定长。
        final long bound = 10_000_000_000L;
        for (int i = 0; i < BATCH_SIZE; i++) {
            long n = Math.floorMod(random.nextLong(), bound);
            codes[i] = String.format("%0" + CODE_DIGITS + "d", n);
        }
        return codes;
    }
}
