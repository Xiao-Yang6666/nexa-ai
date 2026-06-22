package com.nexa.model.interfaces.api.dto;

import com.nexa.model.application.UpdateModelMetaCommand;

/**
 * 更新模型元数据请求体（接口层入参，F-3016）。对齐 openapi {@code ModelMetaUpdateRequest}。
 *
 * <p>{@code statusOnly=true} 仅改 status（防误清，PRD ML-1）；全量模式覆盖式更新。</p>
 *
 * @param id          模型 id（必填）
 * @param statusOnly  是否仅改状态（可空 → false）
 * @param status      状态码
 * @param modelName   模型名
 * @param description 描述
 * @param icon        图标
 * @param tags        标签串
 * @param vendorId    供应商 id
 * @param endpoints   端点串
 * @param nameRule    命名规则
 */
public record ModelMetaUpdateRequest(Long id,
                                     Boolean statusOnly,
                                     Integer status,
                                     String modelName,
                                     String description,
                                     String icon,
                                     String tags,
                                     Long vendorId,
                                     String endpoints,
                                     String nameRule) {

    /**
     * 转应用层命令（statusOnly 缺省 false）。
     *
     * @return 更新命令
     */
    public UpdateModelMetaCommand toCommand() {
        boolean only = statusOnly != null && statusOnly;
        return new UpdateModelMetaCommand(id, only, status, modelName, description, icon, tags,
                vendorId, endpoints, nameRule);
    }
}
