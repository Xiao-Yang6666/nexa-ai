package com.nexa.prefill.interfaces.api.dto;

import com.nexa.prefill.application.CreatePrefillGroupCommand;

import java.util.List;

/**
 * 预填分组创建请求（管理端入参，对齐 openapi {@code PrefillGroupCreateRequest}）。
 *
 * <p>字段名经全局 Jackson SNAKE_CASE 反序列化（请求体直接以 {@code name/type/items/description}
 * 命名，均为单词无需转换）。{@code name}/{@code type} 必填（openapi required），缺失/非法由 domain
 * 校验抛 400。</p>
 *
 * @param name        分组名称（必填）
 * @param type        类型字面量（必填，model/tag/endpoint）
 * @param items       条目列表（可空）
 * @param description 描述（可空）
 */
public record PrefillGroupCreateRequest(String name, String type, List<String> items, String description) {

    /**
     * 转换为应用层创建命令。
     *
     * @return 创建命令
     */
    public CreatePrefillGroupCommand toCommand() {
        return new CreatePrefillGroupCommand(name, type, items, description);
    }
}
