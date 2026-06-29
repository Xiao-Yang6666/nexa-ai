package com.nexa.domain.relay.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * StopReason 双向映射单元测试（RL-8 D4 finish_reason ⇄ stop_reason）。
 */
class StopReasonTest {

    @Test
    void openAiRoundTrip() {
        assertEquals(StopReason.END_TURN, StopReason.fromOpenAi("stop"));
        assertEquals(StopReason.MAX_TOKENS, StopReason.fromOpenAi("length"));
        assertEquals(StopReason.TOOL_USE, StopReason.fromOpenAi("tool_calls"));
        assertEquals("stop", StopReason.END_TURN.toOpenAi());
        assertEquals("length", StopReason.MAX_TOKENS.toOpenAi());
        assertEquals("tool_calls", StopReason.TOOL_USE.toOpenAi());
    }

    @Test
    void anthropicRoundTrip() {
        assertEquals(StopReason.END_TURN, StopReason.fromAnthropic("end_turn"));
        assertEquals(StopReason.MAX_TOKENS, StopReason.fromAnthropic("max_tokens"));
        assertEquals(StopReason.STOP_SEQUENCE, StopReason.fromAnthropic("stop_sequence"));
        assertEquals(StopReason.TOOL_USE, StopReason.fromAnthropic("tool_use"));
        assertEquals("end_turn", StopReason.END_TURN.toAnthropic());
        assertEquals("max_tokens", StopReason.MAX_TOKENS.toAnthropic());
        assertEquals("stop_sequence", StopReason.STOP_SEQUENCE.toAnthropic());
        assertEquals("tool_use", StopReason.TOOL_USE.toAnthropic());
    }

    @Test
    void crossProtocolMapping() {
        // D4 验收：OpenAI length → IR MAX_TOKENS → Anthropic max_tokens
        StopReason ir = StopReason.fromOpenAi("length");
        assertEquals("max_tokens", ir.toAnthropic());
        // Anthropic tool_use → IR TOOL_USE → OpenAI tool_calls
        StopReason ir2 = StopReason.fromAnthropic("tool_use");
        assertEquals("tool_calls", ir2.toOpenAi());
    }

    @Test
    void unknownFinishReasonDefaultsToEndTurn() {
        assertEquals(StopReason.END_TURN, StopReason.fromOpenAi("unknown_future_value"));
        assertEquals(StopReason.END_TURN, StopReason.fromOpenAi(null));
        assertEquals(StopReason.END_TURN, StopReason.fromAnthropic(null));
    }
}
