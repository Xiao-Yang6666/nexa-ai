package com.nexa.modelgroup.interfaces.api.dto;

import com.nexa.modelgroup.application.UpdateModelGroupCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型组更新请求（管理端入参，部分更新语义：非 null 才覆盖）。
 *
 * <p>{@code id} 必填（path 传入，body 不含 id 时由控制器组装）。{@code code} 不可改，不在请求中。</p>
 *
 * @param name           新展示名（null=不改）
 * @param basePriceRatio 新基础倍率（null=不改）
 * @param models         新可用模型列表（null=不改）
 * @param accessPolicy   新访问策略字面量（null=不改）
 * @param description    新描述（null=不改）
 */
public record ModelGroupUpdateRequest(String name, BigDecimal basePriceRatio, List<String> models,
                                      String accessPolicy, String description) {

    /**
     * 转换为应用层更新命令（id 由控制器从 path 注入）。
     *
     * @param id 模型组主键（path）
     * @return 更新命令
     */
    public UpdateModelGroupCommand toCommand(long id) {
        return new UpdateModelGroupCommand(id, name, basePriceRatio, models, accessPolicy, description);
    }
}
