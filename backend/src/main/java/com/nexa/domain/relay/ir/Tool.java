package com.nexa.domain.relay.ir;

import java.util.Map;
import java.util.Objects;

/**
 * 工具定义（IR D3，OpenAI {@code tools[].function} ⇄ Anthropic {@code tools[]} 双向归一）。
 *
 * <p>双向映射：
 * <ul>
 *   <li>OpenAI {@code {type:"function", function:{name, description, parameters}}}</li>
 *   <li>Anthropic {@code {name, description, input_schema}}</li>
 * </ul>
 * IR 统一为 {name, description, jsonSchema}，{@code jsonSchema} 是 JSON Schema 对象（描述参数）。</p>
 *
 * @param name        工具名
 * @param description 工具描述（可空）
 * @param jsonSchema  入参 JSON Schema（OpenAI parameters / Anthropic input_schema 来源）
 */
public record Tool(String name, String description, Map<String, Object> jsonSchema) {

    public Tool {
        Objects.requireNonNull(name, "tool name must not be null");
    }
}
