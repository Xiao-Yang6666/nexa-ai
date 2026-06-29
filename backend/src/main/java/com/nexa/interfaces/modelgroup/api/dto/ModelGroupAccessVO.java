package com.nexa.interfaces.modelgroup.api.dto;

import com.nexa.domain.modelgroup.model.ModelGroupAccess;

/**
 * 模型组授权记录视图（管理端出参，授权清单回显）。
 *
 * @param id           授权记录主键
 * @param modelGroupId 模型组主键
 * @param subjectType  主体类型字面量
 * @param subjectId    主体主键
 * @param createdTime  创建时间（epoch 秒）
 */
public record ModelGroupAccessVO(Long id, long modelGroupId, String subjectType,
                                   long subjectId, Long createdTime) {

    /**
     * 由领域聚合投影为视图。
     *
     * @param a 授权记录聚合
     * @return 视图 DTO
     */
    public static ModelGroupAccessVO from(ModelGroupAccess a) {
        return new ModelGroupAccessVO(
                a.id(),
                a.modelGroupId(),
                a.subjectType().wireValue(),
                a.subjectId(),
                a.createdTime());
    }
}
