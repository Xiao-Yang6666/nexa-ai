package com.nexa.application.model;

/**
 * 创建模型元数据命令（应用层入参，F-3015）。
 *
 * <p>接口层把 ModelMetaCreateRequest 转成本命令，隔离 HTTP DTO 与用例（backend-engineer §5）。</p>
 *
 * @param modelName   模型名（必填）
 * @param description 描述（可空）
 * @param icon        图标（可空）
 * @param tags        标签串（可空）
 * @param vendorId    归属供应商 id（可空）
 * @param endpoints   支持端点串（可空）
 * @param nameRule    命名规则（可空）
 */
public record CreateModelMetaCommand(String modelName,
                                     String description,
                                     String icon,
                                     String tags,
                                     Long vendorId,
                                     String endpoints,
                                     String nameRule) {
}
