package com.nexa.relay.domain.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 请求 IR（Chat/Completions 请求的协议无关中间表示，RL-6 / RL-8 核心数据对象）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §4.1 + prd-relay RL-6/RL-8。
 * 本 IR 只强建模 OpenAI ⇄ Anthropic 五大差异点（ADR-COMPAT-01）：
 * <ul>
 *   <li>D1 system 位置：统一收进 {@link #system()} 字段（OpenAI 从 messages[role=system] 取；Anthropic 从顶层 system 取）；</li>
 *   <li>D2 content 结构：统一为 block 数组（OpenAI 字符串/数组 → IR block；序列化回 OpenAI 时纯单 text 退化回字符串）；</li>
 *   <li>D3 tools：统一 {@link Tool} / {@link ToolChoice}（OpenAI function ⇄ Anthropic tool）；</li>
 *   <li>D4 stop_reason：响应侧 {@link ChatRespIR#stopReason()}，请求侧无；</li>
 *   <li>D5 usage：响应侧 {@link ChatRespIR#usage()}，请求侧无。</li>
 * </ul>
 * 其余私有参数走 {@link #passthroughExtras()} 旁路透传（降低首版工作量）。本类不可变，用 Builder 构造。</p>
 *
 * @param model             模型名（恒为 B，序列化时写回目标协议 model 字段）
 * @param system            system 块列表（D1 统一，可为空）
 * @param messages          消息列表（D2 统一 block 数组）
 * @param tools             工具定义列表（D3 统一，可为空）
 * @param toolChoice        工具选择策略（D3，可为 null 表默认）
 * @param stream            流式标记
 * @param maxTokens         最大生成 token（可为 null 表默认 / Anthropic 必填时由 adapter 补默认值）
 * @param temperature       采样温度（可为 null）
 * @param topP              Top-P 采样（可为 null）
 * @param stopSequences     自定义停止序列（可为空）
 * @param metadata          附加 metadata（可为 null，Anthropic 支持）
 * @param passthroughExtras 旁路透传（协议私有参数，不建模直传）
 */
public record ChatIR(
        String model,
        List<ContentBlock> system,
        List<Message> messages,
        List<Tool> tools,
        ToolChoice toolChoice,
        boolean stream,
        Integer maxTokens,
        Double temperature,
        Double topP,
        List<String> stopSequences,
        Map<String, Object> metadata,
        Map<String, Object> passthroughExtras
) {

    public ChatIR {
        Objects.requireNonNull(model, "model must not be null");
        system = system == null ? List.of() : Collections.unmodifiableList(system);
        messages = messages == null ? List.of() : Collections.unmodifiableList(messages);
        tools = tools == null ? List.of() : Collections.unmodifiableList(tools);
        stopSequences = stopSequences == null ? List.of() : Collections.unmodifiableList(stopSequences);
        passthroughExtras = passthroughExtras == null ? Map.of() : Collections.unmodifiableMap(passthroughExtras);
    }

    /** Builder 工厂。 */
    public static Builder builder(String model) {
        return new Builder(model);
    }

    /** ChatIR Builder（逐字段可选设置）。 */
    public static final class Builder {
        private final String model;
        private List<ContentBlock> system = new ArrayList<>();
        private List<Message> messages = new ArrayList<>();
        private List<Tool> tools = new ArrayList<>();
        private ToolChoice toolChoice;
        private boolean stream;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private List<String> stopSequences = new ArrayList<>();
        private Map<String, Object> metadata;
        private Map<String, Object> passthroughExtras = new HashMap<>();

        private Builder(String model) {
            this.model = model;
        }

        public Builder system(List<ContentBlock> system) { this.system = new ArrayList<>(system); return this; }
        public Builder addSystem(ContentBlock block) { this.system.add(block); return this; }
        public Builder messages(List<Message> messages) { this.messages = new ArrayList<>(messages); return this; }
        public Builder addMessage(Message m) { this.messages.add(m); return this; }
        public Builder tools(List<Tool> tools) { this.tools = new ArrayList<>(tools); return this; }
        public Builder toolChoice(ToolChoice tc) { this.toolChoice = tc; return this; }
        public Builder stream(boolean stream) { this.stream = stream; return this; }
        public Builder maxTokens(Integer max) { this.maxTokens = max; return this; }
        public Builder temperature(Double temp) { this.temperature = temp; return this; }
        public Builder topP(Double topP) { this.topP = topP; return this; }
        public Builder stopSequences(List<String> seqs) { this.stopSequences = new ArrayList<>(seqs); return this; }
        public Builder metadata(Map<String, Object> meta) { this.metadata = meta; return this; }
        public Builder passthrough(Map<String, Object> extras) { this.passthroughExtras = new HashMap<>(extras); return this; }
        public Builder putPassthrough(String key, Object value) { this.passthroughExtras.put(key, value); return this; }

        public ChatIR build() {
            return new ChatIR(model, system, messages, tools, toolChoice, stream, maxTokens,
                    temperature, topP, stopSequences, metadata, passthroughExtras);
        }
    }
}
