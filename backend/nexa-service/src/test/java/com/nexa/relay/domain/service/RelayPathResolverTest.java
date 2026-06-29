package com.nexa.relay.domain.service;

import com.nexa.relay.domain.vo.ProtocolFormat;
import com.nexa.relay.domain.vo.RelayDispatch;
import com.nexa.relay.domain.vo.RelayMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RelayPathResolver 单元测试（RL-2 Path2RelayMode 有序前缀匹配铁律验证）。
 */
class RelayPathResolverTest {

    @Test
    void compactMustMatchBeforeResponses() {
        // PRD RL-2 §6 验收：compact 必须先于 responses 匹配
        RelayDispatch d = RelayPathResolver.resolve("/v1/responses/compact");
        assertEquals(RelayMode.RESPONSES_COMPACTION, d.mode());
        assertEquals(ProtocolFormat.OPENAI_RESPONSES_COMPACTION, d.format());
    }

    @Test
    void responsesMatchesAfterCompactCheck() {
        RelayDispatch d = RelayPathResolver.resolve("/v1/responses");
        assertEquals(RelayMode.RESPONSES, d.mode());
        assertEquals(ProtocolFormat.OPENAI_RESPONSES, d.format());
    }

    @Test
    void messagesGoToClaude() {
        RelayDispatch d = RelayPathResolver.resolve("/v1/messages");
        assertEquals(RelayMode.CLAUDE_MESSAGES, d.mode());
        assertEquals(ProtocolFormat.CLAUDE, d.format());
    }

    @Test
    void v1betaModelsPrefixGoesToGemini() {
        RelayDispatch d = RelayPathResolver.resolve("/v1beta/models/gemini-pro:generateContent");
        assertEquals(RelayMode.GEMINI_GENERATE, d.mode());
        assertEquals(ProtocolFormat.GEMINI, d.format());
    }

    @Test
    void embeddingsBySuffixMatchesAnyPath() {
        // 任意 embeddings 后缀（不限定完整路径）
        assertEquals(RelayMode.EMBEDDINGS, RelayPathResolver.resolve("/v1/embeddings").mode());
        assertEquals(RelayMode.EMBEDDINGS, RelayPathResolver.resolve("/custom/path/embeddings").mode());
    }

    @Test
    void imagesVariationsReturnsNotImplemented() {
        // RL-2 §6 验收：images/variations → RelayNotImplemented
        RelayDispatch d = RelayPathResolver.resolve("/v1/images/variations");
        assertEquals(RelayMode.NOT_IMPLEMENTED, d.mode());
    }

    @Test
    void editsLegacyIsIndependent() {
        // edits 区别于 /images/edits
        RelayDispatch d = RelayPathResolver.resolve("/v1/edits");
        assertEquals(RelayMode.EDITS, d.mode());
    }

    @Test
    void unmatchedPathDefaultsToChatCompletions() {
        RelayDispatch d = RelayPathResolver.resolve("/v1/chat/completions");
        assertEquals(RelayMode.CHAT_COMPLETIONS, d.mode());
        assertEquals(ProtocolFormat.OPENAI, d.format());
    }

    @Test
    void nullPathDefaultsToChatCompletions() {
        RelayDispatch d = RelayPathResolver.resolve(null);
        assertEquals(RelayMode.CHAT_COMPLETIONS, d.mode());
    }

    // ---- outboundPath：RL-6 出站路径随目标协议（修复 OpenAI→Anthropic 上游 404）----

    @Test
    void outboundPathPassthroughKeepsOriginal() {
        // 同源直转：原样透传客户 path
        assertEquals("/v1/chat/completions",
                RelayPathResolver.outboundPath(ProtocolFormat.OPENAI, "/v1/chat/completions", true));
    }

    @Test
    void outboundPathOpenAiToClaudeUsesMessages() {
        // OpenAI 入站 → Anthropic 上游：path 必须改为 /v1/messages（否则 404）
        assertEquals("/v1/messages",
                RelayPathResolver.outboundPath(ProtocolFormat.CLAUDE, "/v1/chat/completions", false));
    }

    @Test
    void outboundPathClaudeToOpenAiUsesChatCompletions() {
        // Anthropic 入站 → OpenAI 上游：path 改为 /v1/chat/completions
        assertEquals("/v1/chat/completions",
                RelayPathResolver.outboundPath(ProtocolFormat.OPENAI, "/v1/messages", false));
    }

    @Test
    void outboundPathUnsupportedProtoFallsBackToOriginal() {
        // 未实现转换的协议：原样透传，不阻断
        assertEquals("/v1/embeddings",
                RelayPathResolver.outboundPath(ProtocolFormat.GEMINI, "/v1/embeddings", false));
        assertEquals("/v1/chat/completions",
                RelayPathResolver.outboundPath(null, "/v1/chat/completions", false));
    }
}
