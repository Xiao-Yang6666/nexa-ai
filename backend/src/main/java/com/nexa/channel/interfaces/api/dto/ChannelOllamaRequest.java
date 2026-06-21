package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ollama 模型拉取/删除请求 DTO（接口层，对齐 openapi ChannelOllamaRequest，F-2027）。
 *
 * @param model 模型名（必填，空白由用例拒绝）
 */
public record ChannelOllamaRequest(@JsonProperty("model") String model) {
}
