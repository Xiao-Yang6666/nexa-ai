package com.nexa.prefill.interfaces.api.dto;

import com.nexa.prefill.application.UpdatePrefillGroupCommand;

import java.util.List;

/**
 * 预填分组更新请求（管理端入参，对齐 openapi {@code PrefillGroupUpdateRequest}）。
 *
 * <p>{@code id} 必填（openapi required，定位被更新分组）；{@code name}/{@code items} 可空——为 null
 * 表示该字段不变（部分更新）。type 不在请求体（openapi UpdateRequest 不含 type，类型不可改）。</p>
 *
 * @param id    被更新分组主键（必填）
 * @param name  新名称（null = 不改名）
 * @param items 新条目列表（null = 不改条目）
 */
public record PrefillGroupUpdateRequest(Long id, String name, List<String> items) {

    /**
     * 转换为应用层更新命令。
     *
     * @return 更新命令
     */
    public UpdatePrefillGroupCommand toCommand() {
        return new UpdatePrefillGroupCommand(id, name, items);
    }
}
