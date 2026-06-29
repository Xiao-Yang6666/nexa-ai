package com.nexa.domain.token.vo;

import com.nexa.domain.token.exception.InvalidTokenParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 令牌域值对象单测（纯 JUnit）：{@link TokenStatus}/{@link TokenKey}/{@link Pagination}。
 *
 * <p>覆盖状态码解析与校验、明文 key 脱敏边界、分页归一（backend-engineer §3.3 正常/边界/异常）。</p>
 */
@DisplayName("令牌域值对象")
class TokenValueObjectsTest {

    // ---- TokenStatus ----

    @Test
    @DisplayName("TokenStatus.fromCode：1→启用，其余→禁用（脏码归并）")
    void statusFromCode() {
        assertEquals(TokenStatus.ENABLED, TokenStatus.fromCode(1));
        assertEquals(TokenStatus.DISABLED, TokenStatus.fromCode(2));
        assertEquals(TokenStatus.DISABLED, TokenStatus.fromCode(3), "历史派生码归并禁用");
        assertEquals(TokenStatus.DISABLED, TokenStatus.fromCode(99));
    }

    @Test
    @DisplayName("TokenStatus.requireValid：仅 1/2 合法，其余抛异常")
    void statusRequireValid() {
        assertEquals(TokenStatus.ENABLED, TokenStatus.requireValid(1));
        assertEquals(TokenStatus.DISABLED, TokenStatus.requireValid(2));
        assertThrows(InvalidTokenParameterException.class, () -> TokenStatus.requireValid(0));
        assertThrows(InvalidTokenParameterException.class, () -> TokenStatus.requireValid(3));
        assertThrows(InvalidTokenParameterException.class, () -> TokenStatus.requireValid(null));
    }

    // ---- TokenKey 脱敏 ----

    @Test
    @DisplayName("TokenKey.mask：长 key 保留头 6 尾 4")
    void maskLongKey() {
        String key = "sk-abcdefghijklmnopqrstuvwxyz";
        String masked = TokenKey.mask(key);
        assertEquals("sk-abc" + "***" + "wxyz", masked);
    }

    @Test
    @DisplayName("TokenKey.mask：短 key（≤8）整体 ***")
    void maskShortKey() {
        assertEquals("***", TokenKey.mask("sk-12345"));
        assertEquals("***", TokenKey.mask("abc"));
    }

    @Test
    @DisplayName("TokenKey.mask：null/空 → 空串")
    void maskNullOrEmpty() {
        assertEquals("", TokenKey.mask(null));
        assertEquals("", TokenKey.mask(""));
    }

    // ---- Pagination 归一 ----

    @Test
    @DisplayName("Pagination.of：null/非正归一为缺省页 1、每页 10")
    void paginationDefaults() {
        Pagination p = Pagination.of(null, null);
        assertEquals(1, p.page());
        assertEquals(10, p.pageSize());

        Pagination p2 = Pagination.of(0, -5);
        assertEquals(1, p2.page());
        assertEquals(10, p2.pageSize());
    }

    @Test
    @DisplayName("Pagination.of：超上限每页归一为 100；offset 计算正确")
    void paginationCapAndOffset() {
        Pagination p = Pagination.of(3, 200);
        assertEquals(3, p.page());
        assertEquals(100, p.pageSize(), "超上限归一 100");
        assertEquals((3 - 1) * 100, p.offset());
    }
}
