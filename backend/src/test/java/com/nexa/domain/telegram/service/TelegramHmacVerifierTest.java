package com.nexa.domain.telegram.service;

import com.nexa.domain.telegram.exception.InvalidTelegramAuthException;
import com.nexa.domain.telegram.vo.TelegramAuthData;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TelegramHmacVerifier 单元测试（F-1051/F-1053 HMAC 防伪铁律）。
 *
 * <p>覆盖正常 + 篡改 + 异常三类（backend-engineer §3.3）：
 * <ul>
 *   <li>正常：用 Bot Token 正确签名的数据校验通过；</li>
 *   <li>篡改：改动任一签名字段后重算 hash 不等于回传 hash → 拒绝（F-1053 核心）；</li>
 *   <li>异常：Token 未配置 → 失败；hash 完全错误 → 失败。</li>
 * </ul>
 * 测试自行用与生产同一算法（SHA256(token) 作 HMAC 密钥）生成「真」hash，确保不是循环论证
 * 而是验证生产实现与 Telegram 协议一致。</p>
 */
class TelegramHmacVerifierTest {

    private static final String BOT_TOKEN = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11";

    /**
     * 用 Telegram 协议算法对给定字段集计算真实 hash（secretKey = SHA256(token)）。
     */
    private static String computeRealHash(Map<String, String> fields, String botToken) throws Exception {
        // data-check-string：按键字典序 key=value 用 \n 连接（与生产 TelegramAuthData 一致）。
        String dcs = new java.util.TreeMap<>(fields).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        byte[] secret = MessageDigest.getInstance("SHA-256").digest(botToken.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] sig = mac.doFinal(dcs.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(sig);
    }

    private static Map<String, String> baseSignedFields() {
        Map<String, String> m = new HashMap<>();
        m.put("id", "111222333");
        m.put("auth_date", "1718000000");
        m.put("first_name", "Alice");
        m.put("username", "alice_tg");
        return m;
    }

    @Test
    void authenticDataPassesVerification() throws Exception {
        Map<String, String> signed = baseSignedFields();
        String realHash = computeRealHash(signed, BOT_TOKEN);

        Map<String, String> params = new HashMap<>(signed);
        params.put("hash", realHash);
        TelegramAuthData authData = TelegramAuthData.fromParams(params);

        // 正常：正确签名校验通过。
        assertTrue(TelegramHmacVerifier.isAuthentic(authData, BOT_TOKEN));
    }

    @Test
    void tamperedFieldFailsVerification() throws Exception {
        Map<String, String> signed = baseSignedFields();
        String realHash = computeRealHash(signed, BOT_TOKEN);

        // 篡改 id（攻击者冒充他人），hash 仍是原数据的 → 重算必不等（F-1053）。
        Map<String, String> params = new HashMap<>(signed);
        params.put("id", "999999999");   // 篡改！
        params.put("hash", realHash);
        TelegramAuthData tampered = TelegramAuthData.fromParams(params);

        assertFalse(TelegramHmacVerifier.isAuthentic(tampered, BOT_TOKEN));
        // requireAuthentic 应抛领域异常拒绝。
        assertThrows(InvalidTelegramAuthException.class,
                () -> TelegramHmacVerifier.requireAuthentic(tampered, BOT_TOKEN));
    }

    @Test
    void injectedUnsignedFieldFailsVerification() throws Exception {
        Map<String, String> signed = baseSignedFields();
        String realHash = computeRealHash(signed, BOT_TOKEN);

        // 攻击者注入一个原签名时不存在的字段：因为 TelegramAuthData 对收到的所有非 hash 字段签名，
        // 注入字段进入 data-check-string，重算 hash 必不等 → 拒绝（防字段注入绕过）。
        Map<String, String> params = new HashMap<>(signed);
        params.put("photo_url", "http://evil.example/x.jpg"); // 注入！
        params.put("hash", realHash);
        TelegramAuthData injected = TelegramAuthData.fromParams(params);

        assertFalse(TelegramHmacVerifier.isAuthentic(injected, BOT_TOKEN));
    }

    @Test
    void wrongTokenFailsVerification() throws Exception {
        Map<String, String> signed = baseSignedFields();
        String realHash = computeRealHash(signed, BOT_TOKEN);

        Map<String, String> params = new HashMap<>(signed);
        params.put("hash", realHash);
        TelegramAuthData authData = TelegramAuthData.fromParams(params);

        // 用错误的 Bot Token 校验（伪造服务端/token 不符）→ 失败。
        assertFalse(TelegramHmacVerifier.isAuthentic(authData, "000000:WRONGTOKENwrongtokenWRONGtoken000"));
    }

    @Test
    void missingTokenThrows() throws Exception {
        Map<String, String> signed = baseSignedFields();
        String realHash = computeRealHash(signed, BOT_TOKEN);
        Map<String, String> params = new HashMap<>(signed);
        params.put("hash", realHash);
        TelegramAuthData authData = TelegramAuthData.fromParams(params);

        // Token 未配置：明确失败而非静默放行（安全默认）。
        assertThrows(InvalidTelegramAuthException.class,
                () -> TelegramHmacVerifier.isAuthentic(authData, ""));
        assertThrows(InvalidTelegramAuthException.class,
                () -> TelegramHmacVerifier.isAuthentic(authData, null));
    }
}
