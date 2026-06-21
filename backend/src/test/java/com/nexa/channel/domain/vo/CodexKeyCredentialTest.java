package com.nexa.channel.domain.vo;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CodexKeyCredential} 值对象单测（纯 JUnit，零 Spring/DB，F-4045）。
 *
 * <p>覆盖 Codex 渠道 OAuth 凭证解析与必填不变量（API-ENDPOINTS §5.8），按正常/边界/异常组织
 * （backend-engineer §3.3）：三段/两段解析、refresh_token 可选、access_token/account_id 缺失拒绝、
 * 空 key 拒绝、脱敏 toString。</p>
 */
@DisplayName("CodexKeyCredential 值对象")
class CodexKeyCredentialTest {

    // ---- parse：正常 ----

    @Test
    @DisplayName("parse：三段 access|account|refresh → 全部解析，canRefresh=true")
    void parseThreeSegments() {
        CodexKeyCredential cred = CodexKeyCredential.parse("acc-tok|account-123|ref-tok");

        assertEquals("acc-tok", cred.accessToken());
        assertEquals("account-123", cred.accountId());
        assertEquals("ref-tok", cred.refreshToken());
        assertTrue(cred.canRefresh(), "有 refresh_token → 可刷新");
    }

    @Test
    @DisplayName("parse：两段 access|account（无 refresh）→ refreshToken=null，canRefresh=false")
    void parseTwoSegments() {
        CodexKeyCredential cred = CodexKeyCredential.parse("acc-tok|account-123");

        assertEquals("acc-tok", cred.accessToken());
        assertEquals("account-123", cred.accountId());
        assertNull(cred.refreshToken(), "无 refresh 段 → null");
        assertFalse(cred.canRefresh(), "无 refresh_token → 不可刷新");
    }

    @Test
    @DisplayName("parse：尾部空 refresh 段（access|account|）→ refreshToken 归一为 null")
    void parseEmptyRefreshSegment() {
        CodexKeyCredential cred = CodexKeyCredential.parse("acc-tok|account-123|");

        assertFalse(cred.canRefresh(), "空 refresh 段归一为 null");
    }

    @Test
    @DisplayName("parse：各段含空白 → trim 归一")
    void parseTrimsSegments() {
        CodexKeyCredential cred = CodexKeyCredential.parse("  acc-tok | account-123 | ref-tok ");

        assertEquals("acc-tok", cred.accessToken());
        assertEquals("account-123", cred.accountId());
        assertEquals("ref-tok", cred.refreshToken());
    }

    // ---- parse：异常（必填缺失 → 400 语义） ----

    @Test
    @DisplayName("parse：空 key → InvalidChannelParameterException")
    void parseEmptyKeyRejected() {
        assertThrows(InvalidChannelParameterException.class, () -> CodexKeyCredential.parse(""));
        assertThrows(InvalidChannelParameterException.class, () -> CodexKeyCredential.parse("   "));
        assertThrows(InvalidChannelParameterException.class, () -> CodexKeyCredential.parse(null));
    }

    @Test
    @DisplayName("parse：仅一段（缺 account_id）→ account_id missing 拒绝")
    void parseMissingAccountIdRejected() {
        InvalidChannelParameterException ex = assertThrows(
                InvalidChannelParameterException.class,
                () -> CodexKeyCredential.parse("acc-tok"));
        assertTrue(ex.getMessage().contains("account_id"), "报 account_id 缺失");
    }

    @Test
    @DisplayName("parse：access_token 段为空（|account-123）→ access_token missing 拒绝")
    void parseMissingAccessTokenRejected() {
        InvalidChannelParameterException ex = assertThrows(
                InvalidChannelParameterException.class,
                () -> CodexKeyCredential.parse("|account-123|ref"));
        assertTrue(ex.getMessage().contains("access_token"), "报 access_token 缺失");
    }

    // ---- 安全：脱敏 toString ----

    @Test
    @DisplayName("toString：绝不回显 token 明文（脱敏 ***）")
    void toStringMasksSecrets() {
        CodexKeyCredential cred = CodexKeyCredential.parse("super-secret-access|account-123|super-secret-refresh");
        String s = cred.toString();

        assertFalse(s.contains("super-secret-access"), "access_token 不得明文出现");
        assertFalse(s.contains("super-secret-refresh"), "refresh_token 不得明文出现");
        assertTrue(s.contains("***"), "敏感字段脱敏为 ***");
        assertTrue(s.contains("account-123"), "account_id 非凭证、可显示便于排障");
    }

    // ---- 值对象相等性 ----

    @Test
    @DisplayName("equals：相同三要素 → 相等且 hashCode 一致")
    void valueEquality() {
        CodexKeyCredential a = CodexKeyCredential.parse("acc|account|ref");
        CodexKeyCredential b = CodexKeyCredential.parse("acc|account|ref");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
