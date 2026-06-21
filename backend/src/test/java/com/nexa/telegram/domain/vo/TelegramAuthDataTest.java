package com.nexa.telegram.domain.vo;

import com.nexa.telegram.domain.exception.InvalidTelegramAuthException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TelegramAuthData 单元测试（F-1051：data-check-string 规范化 + 字段校验）。
 *
 * <p>验证 data-check-string 严格按键字典序拼 {@code key=value}、用 {@code \n} 连接、排除 hash，
 * 以及 id/hash/auth_date 缺失或格式非法时的拒绝（这是 HMAC 校验输入正确性的前提）。</p>
 */
class TelegramAuthDataTest {

    /** 一个格式合法的 64 位 hex（仅用于通过格式校验，不参与本测试的真伪判定）。 */
    private static final String DUMMY_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void dataCheckStringIsSortedAndExcludesHash() {
        // 故意乱序插入，验证输出按键字典序、且不含 hash 行。
        Map<String, String> params = new LinkedHashMap<>();
        params.put("username", "bob");
        params.put("id", "555");
        params.put("auth_date", "1700000000");
        params.put("first_name", "Bob");
        params.put("hash", DUMMY_HASH);

        TelegramAuthData data = TelegramAuthData.fromParams(params);
        // 字典序：auth_date < first_name < id < username（hash 被排除）。
        String expected = "auth_date=1700000000\n"
                + "first_name=Bob\n"
                + "id=555\n"
                + "username=bob";
        assertEquals(expected, data.dataCheckString());
        assertTrue(data.dataCheckString().indexOf("hash=") < 0, "hash must not appear in data-check-string");
        assertEquals("555", data.telegramId().value());
        assertEquals(1700000000L, data.authDate());
    }

    @Test
    void missingHashRejected() {
        Map<String, String> params = new HashMap<>();
        params.put("id", "555");
        params.put("auth_date", "1700000000");
        // 无 hash → 无法校验来源，拒绝。
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramAuthData.fromParams(params));
    }

    @Test
    void malformedHashRejected() {
        Map<String, String> params = new HashMap<>();
        params.put("id", "555");
        params.put("auth_date", "1700000000");
        params.put("hash", "not-a-valid-hex"); // 非 64 位 hex → 视为伪造。
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramAuthData.fromParams(params));
    }

    @Test
    void missingOrNonNumericIdRejected() {
        Map<String, String> noId = new HashMap<>();
        noId.put("auth_date", "1700000000");
        noId.put("hash", DUMMY_HASH);
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramAuthData.fromParams(noId));

        Map<String, String> badId = new HashMap<>();
        badId.put("id", "abc"); // Telegram id 恒为数字串。
        badId.put("auth_date", "1700000000");
        badId.put("hash", DUMMY_HASH);
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramAuthData.fromParams(badId));
    }

    @Test
    void missingOrNonNumericAuthDateRejected() {
        Map<String, String> noDate = new HashMap<>();
        noDate.put("id", "555");
        noDate.put("hash", DUMMY_HASH);
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramAuthData.fromParams(noDate));

        Map<String, String> badDate = new HashMap<>();
        badDate.put("id", "555");
        badDate.put("auth_date", "yesterday");
        badDate.put("hash", DUMMY_HASH);
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramAuthData.fromParams(badDate));
    }
}
