package com.nexa.telegram.domain.service;

import com.nexa.telegram.domain.exception.InvalidTelegramAuthException;
import com.nexa.telegram.domain.vo.TelegramAuthData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Telegram Login Widget HMAC 防伪校验领域服务（F-1051/F-1053 的核心业务规则）。
 *
 * <p>这是 D4 Telegram 登录 Bot 的<b>安全心脏</b>。Telegram 官方约定的校验算法：
 * <ol>
 *   <li>{@code secretKey = SHA256(botToken)}（注意是对 token 取 SHA256 得到密钥字节，<b>不是</b>把 token 当密钥）；</li>
 *   <li>{@code computed = HMAC-SHA256(secretKey, dataCheckString)}，十六进制小写；</li>
 *   <li>{@code computed == 回传 hash} 才算真实来自 Telegram、且参数未被篡改。</li>
 * </ol>
 * 任何一个签名字段被篡改，重算 {@code computed} 即不等于回传 {@code hash}（F-1053「篡改任一参数 →
 * 重算 hash 不等 → 拒绝」）。比较用<b>常数时间</b>（{@link MessageDigest#isEqual}）防时序侧信道。</p>
 *
 * <p>DDD 定位：本类是<b>领域服务</b>（跨值对象的纯业务规则，不属于单一 VO），零框架依赖
 * （仅用 JDK {@code javax.crypto}/{@code java.security} 标准库，与聚合用 JDK 一致），可纯 JUnit 单测
 * （backend-engineer §2.4 领域服务、§3.3 核心逻辑必测）。Bot Token 由应用层从 {@code TelegramSettings}
 * 端口取出后传入，本服务不读配置（保持领域纯净）。</p>
 */
public final class TelegramHmacVerifier {

    private TelegramHmacVerifier() {
        // 无状态领域服务，仅暴露静态行为；禁止实例化。
    }

    /**
     * 校验授权数据的 HMAC 签名是否与 Bot Token 一致（不抛的查询版）。
     *
     * @param authData Telegram 回传授权数据（已含 data-check-string 构造逻辑）
     * @param botToken Telegram Bot Token（机密，应用层从配置取，绝不落库/下发）
     * @return 签名一致返回 {@code true}（参数未被篡改、确来自 Telegram），否则 {@code false}
     * @throws InvalidTelegramAuthException Bot Token 未配置（空），无法校验
     */
    public static boolean isAuthentic(TelegramAuthData authData, String botToken) {
        Objects.requireNonNull(authData, "authData");
        if (botToken == null || botToken.isBlank()) {
            // Token 缺失则任何签名都无从校验——视为配置错误，明确失败而非静默放行（安全默认）。
            throw new InvalidTelegramAuthException("telegram bot token is not configured");
        }

        byte[] secretKey = sha256(botToken.getBytes(StandardCharsets.UTF_8));
        byte[] computed = hmacSha256(secretKey,
                authData.dataCheckString().getBytes(StandardCharsets.UTF_8));
        String computedHex = toHexLower(computed);

        // 常数时间比较：避免按字符逐位短路比较泄露 hash 前缀（时序侧信道）。
        return MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                authData.hash().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验授权数据签名，不通过即拒绝（命令版护栏，F-1053）。
     *
     * <p>在 {@link #isAuthentic} 基础上把「不一致」翻译为领域异常，供登录/绑定用例直接调用
     * 而无需自行写 if-throw。message 稳定不回显 hash 细节（不给攻击者反馈）。</p>
     *
     * @param authData Telegram 回传授权数据
     * @param botToken Telegram Bot Token（机密）
     * @throws InvalidTelegramAuthException 签名不一致（参数被篡改/伪造）或 Token 未配置
     */
    public static void requireAuthentic(TelegramAuthData authData, String botToken) {
        if (!isAuthentic(authData, botToken)) {
            throw new InvalidTelegramAuthException("telegram login verification failed");
        }
    }

    /**
     * 计算 SHA-256 摘要（用于由 Bot Token 派生 HMAC secretKey）。
     *
     * @param data 输入字节
     * @return 32 字节摘要
     */
    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，缺失属运行环境异常——wrap 不吞错，向上暴露根因。
            throw new IllegalStateException("SHA-256 algorithm unavailable in this JVM", e);
        }
    }

    /**
     * 计算 HMAC-SHA256。
     *
     * @param key  密钥字节（此处为 SHA256(botToken)）
     * @param data 待签名字节（data-check-string）
     * @return HMAC 字节
     */
    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 computation failed", e);
        }
    }

    /**
     * 字节数组转十六进制小写串。
     *
     * @param bytes 输入字节
     * @return hex 小写串
     */
    private static String toHexLower(byte[] bytes) {
        // 使用 JDK HexFormat（与 account 聚合占位密码生成保持同一工具）。
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
