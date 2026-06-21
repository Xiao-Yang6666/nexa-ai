package com.nexa.channel.domain.model;

import com.nexa.channel.domain.exception.ChannelOperationNotSupportedException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.channel.domain.vo.ChannelType;
import com.nexa.channel.domain.vo.CodexKeyCredential;
import com.nexa.channel.domain.vo.MultiKeyMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Channel} 聚合根 Codex 运维相关充血行为单测（纯 JUnit，零 Spring/DB，F-4045）。
 *
 * <p>覆盖 Codex 渠道护栏与凭证回写规则（API-ENDPOINTS §5.8），按正常/边界/异常组织
 * （backend-engineer §3.3）：ensureCodex（非 Codex 文案逐字对齐）、ensureSingleKey（multi-key 文案）、
 * codexCredential 解析、refreshCodexKey 回写、shouldReinitCacheAfterKeyRefresh 的 status∈{1,3} 规则。</p>
 */
@DisplayName("Channel 聚合根 - Codex 运维（F-4045）")
class ChannelCodexTest {

    private static final int CODEX_TYPE = ChannelType.CODEX;   // 54
    private static final int OPENAI_TYPE = 1;                  // 非 Codex

    /** 构造单 Key Codex 渠道（key 为合法三段凭证）。 */
    private static Channel codexSingleKey() {
        return Channel.create(CODEX_TYPE, "acc-tok|account-123|ref-tok", "gpt-5-codex",
                "codex-ch", null, null, null, null, null, null, null, null, null, null);
    }

    // ---- ensureCodex ----

    @Test
    @DisplayName("ensureCodex：Codex 渠道 → 通过")
    void ensureCodexPasses() {
        codexSingleKey().ensureCodex();
    }

    @Test
    @DisplayName("ensureCodex：非 Codex 渠道 → 抛 'channel type is not Codex'（文案逐字对齐契约）")
    void ensureCodexRejectsNonCodex() {
        Channel openai = Channel.create(OPENAI_TYPE, "sk-x", "gpt-4o", "ch", null,
                null, null, null, null, null, null, null, null, null);
        ChannelOperationNotSupportedException ex = assertThrows(
                ChannelOperationNotSupportedException.class, openai::ensureCodex);
        assertEquals("channel type is not Codex", ex.getMessage());
    }

    // ---- ensureSingleKey ----

    @Test
    @DisplayName("ensureSingleKey：单 Key 渠道 → 通过")
    void ensureSingleKeyPasses() {
        codexSingleKey().ensureSingleKey();
    }

    @Test
    @DisplayName("ensureSingleKey：multi-key 渠道 → 抛 'multi-key channel is not supported'（文案逐字对齐）")
    void ensureSingleKeyRejectsMultiKey() {
        ChannelInfo multi = new ChannelInfo(true, 3, MultiKeyMode.RANDOM, 0);
        Channel multiKeyCodex = Channel.create(CODEX_TYPE, "acc|account|ref", "gpt-5-codex",
                "codex-multi", null, null, null, null, null, null, null, null, null, multi);
        ChannelOperationNotSupportedException ex = assertThrows(
                ChannelOperationNotSupportedException.class, multiKeyCodex::ensureSingleKey);
        assertEquals("multi-key channel is not supported", ex.getMessage());
    }

    // ---- codexCredential 解析 ----

    @Test
    @DisplayName("codexCredential：合法三段 key → 解析出 access/account/refresh")
    void codexCredentialParses() {
        CodexKeyCredential cred = codexSingleKey().codexCredential();
        assertEquals("acc-tok", cred.accessToken());
        assertEquals("account-123", cred.accountId());
        assertTrue(cred.canRefresh());
    }

    @Test
    @DisplayName("codexCredential：key 缺 account_id → InvalidChannelParameterException（→400）")
    void codexCredentialRejectsMissingAccount() {
        Channel bad = Channel.create(CODEX_TYPE, "only-access-token", "gpt-5-codex",
                "codex-bad", null, null, null, null, null, null, null, null, null, null);
        assertThrows(InvalidChannelParameterException.class, bad::codexCredential);
    }

    // ---- refreshCodexKey 回写 ----

    @Test
    @DisplayName("refreshCodexKey：回写新 key → codexCredential 解析为新凭证")
    void refreshCodexKeyRewrites() {
        Channel c = codexSingleKey();
        c.refreshCodexKey("new-access|account-123|new-refresh");

        CodexKeyCredential cred = c.codexCredential();
        assertEquals("new-access", cred.accessToken());
        assertEquals("new-refresh", cred.refreshToken());
    }

    @Test
    @DisplayName("refreshCodexKey：空白新 key → InvalidChannelParameterException（不清空凭证）")
    void refreshCodexKeyRejectsBlank() {
        Channel c = codexSingleKey();
        assertThrows(InvalidChannelParameterException.class, () -> c.refreshCodexKey("  "));
    }

    // ---- shouldReinitCacheAfterKeyRefresh：status∈{1,3} 规则 ----

    @Test
    @DisplayName("shouldReinitCacheAfterKeyRefresh：默认启用(1) → true")
    void reinitCacheWhenEnabled() {
        // create 默认 Status=ENABLED(1)
        assertTrue(codexSingleKey().shouldReinitCacheAfterKeyRefresh());
    }

    @Test
    @DisplayName("shouldReinitCacheAfterKeyRefresh：自动禁用(3) → true")
    void reinitCacheWhenAutoDisabled() {
        // rehydrate status=3（AUTO_DISABLED）
        Channel autoDisabled = Channel.rehydrate(7L, CODEX_TYPE, "acc|account|ref", 3, "codex",
                0, "", "gpt-5-codex", "default", 0L, 1, null, 0L, null, null, null, "", null, null,
                ChannelInfo.single(), 0L);
        assertTrue(autoDisabled.shouldReinitCacheAfterKeyRefresh());
    }

    @Test
    @DisplayName("shouldReinitCacheAfterKeyRefresh：手动禁用(2) → false（不参与路由无需重建缓存）")
    void noReinitCacheWhenManuallyDisabled() {
        Channel manuallyDisabled = Channel.rehydrate(7L, CODEX_TYPE, "acc|account|ref", 2, "codex",
                0, "", "gpt-5-codex", "default", 0L, 1, null, 0L, null, null, null, "", null, null,
                ChannelInfo.single(), 0L);
        assertFalse(manuallyDisabled.shouldReinitCacheAfterKeyRefresh());
    }
}
