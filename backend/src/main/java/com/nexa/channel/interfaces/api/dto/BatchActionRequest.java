package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 批量操作渠道请求 DTO（接口层，对齐 openapi BatchActionRequest，F-2016）。
 *
 * <p>幂等键 (ids, action)。ids 必填，action 为 enable/disable/delete（校验在用例）。</p>
 *
 * @param ids    渠道 id 集合（必填）
 * @param action 操作类型（enable/disable/delete）
 */
public record BatchActionRequest(
        @JsonProperty("ids") List<Long> ids,
        @JsonProperty("action") String action) {
}
