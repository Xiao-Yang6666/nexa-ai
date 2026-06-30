package com.nexa.application.model.command;

/**
 * 更新模型元数据命令（应用层入参，F-3016）。
 *
 * <p>{@code statusOnly=true} 仅改 status，不动其他字段（防误清，PRD ML-1）。
 * 全量模式下未提供的字段按业务等价为「清空」（覆盖式更新语义，PRD ML-1）。</p>
 *
 * @param id          模型 id（必填）
 * @param statusOnly  是否仅改状态
 * @param status      状态码（statusOnly=true 时必填，全量模式可空 → 不改）
 * @param modelName   模型名（全量模式必填）
 * @param description 描述
 * @param icon        图标
 * @param tags        标签串
 * @param vendorId    供应商 id
 * @param endpoints   端点串
 * @param nameRule    命名规则
 */
public record UpdateModelMetaCommand(Long id,
                                     boolean statusOnly,
                                     Integer status,
                                     String modelName,
                                     String description,
                                     String icon,
                                     String tags,
                                     Long vendorId,
                                     String endpoints,
                                     String nameRule) {
}
