package com.nexa.modelgroup.interfaces.api.dto;

/**
 * 模型组访问授权请求（管理端入参，授权某用户/令牌访问私有模型组）。
 *
 * @param subjectType 主体类型字面量（USER/TOKEN，必填）
 * @param subjectId   主体主键（userId 或 tokenId，必填 &gt;0）
 */
public record ModelGroupAccessRequest(String subjectType, Long subjectId) {
}
