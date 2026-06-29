package com.nexa.relay.domain.ir;

/**
 * IR 停止原因枚举（RL-8 D4 stop_reason 双向映射的协议无关枚举）。
 *
 * <p>领域规则来源：prd-relay.md RL-8 D4 + COMPAT-LAYER-DATA-OBJECTS §4.3。
 * 双向映射规则（API-STATE §错误/停止映射）：
 * <pre>
 *   IR              | OpenAI finish_reason | Anthropic stop_reason
 *   ----------------|----------------------|-----------------------
 *   END_TURN        | "stop"               | "end_turn"
 *   MAX_TOKENS      | "length"             | "max_tokens"
 *   STOP_SEQUENCE   | "stop"               | "stop_sequence"
 *   TOOL_USE        | "tool_calls"         | "tool_use"
 *   CONTENT_FILTER  | "content_filter"     | "end_turn"（兜底，Anthropic 无对等）
 * </pre>
 * </p>
 */
public enum StopReason {

    END_TURN,
    MAX_TOKENS,
    STOP_SEQUENCE,
    TOOL_USE,
    CONTENT_FILTER;

    /** OpenAI {@code finish_reason} 三态线值。 */
    public static final String OPENAI_STOP = "stop";
    public static final String OPENAI_LENGTH = "length";
    public static final String OPENAI_TOOL_CALLS = "tool_calls";
    public static final String OPENAI_CONTENT_FILTER = "content_filter";

    /** Anthropic {@code stop_reason} 四态线值。 */
    public static final String ANTHROPIC_END_TURN = "end_turn";
    public static final String ANTHROPIC_MAX_TOKENS = "max_tokens";
    public static final String ANTHROPIC_STOP_SEQUENCE = "stop_sequence";
    public static final String ANTHROPIC_TOOL_USE = "tool_use";

    /**
     * OpenAI {@code finish_reason} → IR。
     *
     * <p>不可识别值兜底为 {@link #END_TURN}（前向兼容，避免上游加新值导致 NPE）。</p>
     */
    public static StopReason fromOpenAi(String finishReason) {
        if (finishReason == null) {
            return END_TURN;
        }
        return switch (finishReason) {
            case OPENAI_STOP -> END_TURN;
            case OPENAI_LENGTH -> MAX_TOKENS;
            case OPENAI_TOOL_CALLS -> TOOL_USE;
            case OPENAI_CONTENT_FILTER -> CONTENT_FILTER;
            default -> END_TURN;
        };
    }

    /** IR → OpenAI {@code finish_reason}。STOP_SEQUENCE 也并入 "stop"（OpenAI 无对等）。 */
    public String toOpenAi() {
        return switch (this) {
            case END_TURN, STOP_SEQUENCE -> OPENAI_STOP;
            case MAX_TOKENS -> OPENAI_LENGTH;
            case TOOL_USE -> OPENAI_TOOL_CALLS;
            case CONTENT_FILTER -> OPENAI_CONTENT_FILTER;
        };
    }

    /** Anthropic {@code stop_reason} → IR。不可识别值兜底为 {@link #END_TURN}。 */
    public static StopReason fromAnthropic(String stopReason) {
        if (stopReason == null) {
            return END_TURN;
        }
        return switch (stopReason) {
            case ANTHROPIC_END_TURN -> END_TURN;
            case ANTHROPIC_MAX_TOKENS -> MAX_TOKENS;
            case ANTHROPIC_STOP_SEQUENCE -> STOP_SEQUENCE;
            case ANTHROPIC_TOOL_USE -> TOOL_USE;
            default -> END_TURN;
        };
    }

    /** IR → Anthropic {@code stop_reason}。CONTENT_FILTER 兜底为 {@code end_turn}（Anthropic 无对等）。 */
    public String toAnthropic() {
        return switch (this) {
            case END_TURN, CONTENT_FILTER -> ANTHROPIC_END_TURN;
            case MAX_TOKENS -> ANTHROPIC_MAX_TOKENS;
            case STOP_SEQUENCE -> ANTHROPIC_STOP_SEQUENCE;
            case TOOL_USE -> ANTHROPIC_TOOL_USE;
        };
    }
}
