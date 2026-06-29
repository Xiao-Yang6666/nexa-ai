package com.nexa.interfaces.model.api.dto;

import com.nexa.application.model.CreateModelMetaCommand;

/**
 * 创建模型元数据请求体（接口层入参，F-3015）。对齐 openapi {@code ModelMetaCreateRequest}。
 *
 * @param modelName   模型名（必填，领域层校验非空）
 * @param description 描述
 * @param icon        图标
 * @param tags        标签串
 * @param vendorId    归属供应商 id
 * @param endpoints   支持端点串
 * @param nameRule    命名规则
 */
public record ModelMetaCreateRequest(String modelName,
                                     String description,
                                     String icon,
                                     String tags,
                                     Long vendorId,
                                     String endpoints,
                                     String nameRule) {

    /**
     * 转应用层命令（接口层 DTO → 用例入参，隔离 HTTP 与用例）。
     *
     * @return 创建命令
     */
    public CreateModelMetaCommand toCommand() {
        return new CreateModelMetaCommand(modelName, description, icon, tags, vendorId, endpoints, nameRule);
    }
}
