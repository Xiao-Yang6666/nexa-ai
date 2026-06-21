package com.nexa.playground.domain;

import com.nexa.playground.domain.exception.InvalidPlaygroundRequestException;
import com.nexa.playground.domain.exception.PlaygroundAccessDeniedException;
import com.nexa.playground.domain.model.PlaygroundChatRequest;
import com.nexa.playground.domain.vo.CredentialKind;
import com.nexa.playground.domain.vo.TempTokenContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Playground 域核心逻辑单测（纯 JUnit，F-4038）。
 *
 * <p>覆盖三块核心领域规则：① CredentialKind 安全闸（禁 access token）；② TempTokenContext
 * playground-&lt;group&gt; 构造 + 不变量；③ PlaygroundChatRequest 聚合根入参校验 + 授权行为。
 * 正常/边界/异常三类（backend-engineer §3.3）。零 Spring，验「禁 access token」关键安全闸可纯单测。</p>
 */
@DisplayName("Playground 域核心逻辑（F-4038）")
class PlaygroundDomainTest {

    // ---------- CredentialKind 安全闸 ----------

    @DisplayName("CredentialKind: access token 调 requireSessionForPlayground → 403 拒绝")
    @Test
    void accessTokenRejected() {
        assertThrows(PlaygroundAccessDeniedException.class,
                () -> CredentialKind.ACCESS_TOKEN.requireSessionForPlayground());
        assertFalse(CredentialKind.ACCESS_TOKEN.allowedForPlayground());
    }

    @DisplayName("CredentialKind: session 凭据放行")
    @Test
    void sessionAllowed() {
        // 不抛即放行
        CredentialKind.SESSION.requireSessionForPlayground();
        assertTrue(CredentialKind.SESSION.allowedForPlayground());
    }

    @DisplayName("PlaygroundAccessDeniedException 携带契约文案与 403")
    @Test
    void accessDeniedContract() {
        PlaygroundAccessDeniedException e = new PlaygroundAccessDeniedException();
        assertEquals(403, e.httpStatus());
        assertEquals("ACCESS_DENIED", e.code());
        assertEquals("暂不支持使用 access token", e.getMessage());
    }

    // ---------- TempTokenContext ----------

    @DisplayName("TempTokenContext: 令牌名 = playground-<group>，分组保留")
    @Test
    void tempTokenNaming() {
        TempTokenContext ctx = TempTokenContext.forUser(42L, "vip");
        assertEquals("playground-vip", ctx.tokenName());
        assertEquals("vip", ctx.group());
        assertEquals(42L, ctx.userId());
    }

    @DisplayName("TempTokenContext: 分组去空白后再拼名")
    @Test
    void tempTokenTrimsGroup() {
        TempTokenContext ctx = TempTokenContext.forUser(1L, "  default ");
        assertEquals("playground-default", ctx.tokenName());
    }

    @DisplayName("TempTokenContext: userId 非正 / group 空白 → 拒绝构造")
    @Test
    void tempTokenInvariants() {
        assertThrows(IllegalArgumentException.class, () -> TempTokenContext.forUser(0L, "g"));
        assertThrows(IllegalArgumentException.class, () -> TempTokenContext.forUser(-1L, "g"));
        assertThrows(IllegalArgumentException.class, () -> TempTokenContext.forUser(1L, "  "));
        assertThrows(IllegalArgumentException.class, () -> TempTokenContext.forUser(1L, null));
    }

    // ---------- PlaygroundChatRequest 聚合根 ----------

    @DisplayName("PlaygroundChatRequest: 合法请求 + session → 授权通过，产出 playground-<group>")
    @Test
    void validRequestAuthorizes() {
        PlaygroundChatRequest req = PlaygroundChatRequest.of(
                7L, "alice", "default", "gpt-4o", false, true, CredentialKind.SESSION);
        req.authorize(); // 不抛
        assertEquals("playground-default", req.toTempTokenContext().tokenName());
        assertEquals("gpt-4o", req.requestedModel());
    }

    @DisplayName("PlaygroundChatRequest: access token 凭据 → authorize 抛 403（关键安全闸）")
    @Test
    void accessTokenRequestDeniedOnAuthorize() {
        PlaygroundChatRequest req = PlaygroundChatRequest.of(
                7L, "alice", "default", "gpt-4o", false, true, CredentialKind.ACCESS_TOKEN);
        assertThrows(PlaygroundAccessDeniedException.class, req::authorize);
    }

    @DisplayName("PlaygroundChatRequest: model 空 / messages 空 / group 空 → 400 InvalidPlaygroundRequest")
    @Test
    void invalidRequestRejected() {
        assertThrows(InvalidPlaygroundRequestException.class, () -> PlaygroundChatRequest.of(
                7L, "a", "default", "  ", false, true, CredentialKind.SESSION));
        assertThrows(InvalidPlaygroundRequestException.class, () -> PlaygroundChatRequest.of(
                7L, "a", "default", "gpt-4o", false, false, CredentialKind.SESSION));
        assertThrows(InvalidPlaygroundRequestException.class, () -> PlaygroundChatRequest.of(
                7L, "a", " ", "gpt-4o", false, true, CredentialKind.SESSION));
    }

    @DisplayName("PlaygroundChatRequest: userId 非正 / credentialKind 空 → IllegalArgument")
    @Test
    void aggregateInvariants() {
        assertThrows(IllegalArgumentException.class, () -> PlaygroundChatRequest.of(
                0L, "a", "default", "gpt-4o", false, true, CredentialKind.SESSION));
        assertThrows(IllegalArgumentException.class, () -> PlaygroundChatRequest.of(
                1L, "a", "default", "gpt-4o", false, true, null));
    }
}
