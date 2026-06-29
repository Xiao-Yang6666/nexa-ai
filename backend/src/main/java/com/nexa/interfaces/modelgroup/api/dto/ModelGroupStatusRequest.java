package com.nexa.interfaces.modelgroup.api.dto;

/**
 * 模型组状态切换请求（管理端入参）。
 *
 * @param status 目标状态整数码（1=启用 2=禁用）
 */
public record ModelGroupStatusRequest(Integer status) {
}
