package com.nexa.interfaces.api.model.dto;

import com.nexa.domain.model.model.ModelMeta;

/**
 * 模型元数据管理视图（接口层出参，AdminView，F-3013~F-3016）。
 *
 * <p>对齐 openapi {@code ModelMetaAdminVO}。模型元数据本身不含成本/上游模型 B/供应商凭证等敏感
 * 信息（仅承载对外模型名 A 与展示/分类元数据），故 AdminView 平铺全字段；vendor 仅为 id 引用，
 * 不内联供应商凭证（客户视图铁律：管理视图也不泄露上游 B/成本）。时间戳为 epoch 秒。</p>
 *
 * @param id          主键
 * @param modelName   模型名（对外名 A）
 * @param status      状态码
 * @param description 描述
 * @param icon        图标
 * @param tags        标签串
 * @param vendorId    归属供应商 id
 * @param endpoints   支持端点串
 * @param nameRule    命名规则
 * @param syncOfficial 同步来源标记
 * @param createdTime 创建时间 epoch 秒
 * @param updatedTime 更新时间 epoch 秒
 */
public record ModelMetaAdminVO(Long id,
                                 String modelName,
                                 int status,
                                 String description,
                                 String icon,
                                 String tags,
                                 Long vendorId,
                                 String endpoints,
                                 String nameRule,
                                 int syncOfficial,
                                 Long createdTime,
                                 Long updatedTime) {

    /**
     * 由领域聚合裁剪为管理视图。
     *
     * @param m 模型聚合
     * @return 管理视图
     */
    public static ModelMetaAdminVO from(ModelMeta m) {
        return new ModelMetaAdminVO(
                m.id(), m.modelName(), m.status().code(), m.description(), m.icon(), m.tags(),
                m.vendorId(), m.endpoints(), m.nameRule(), m.syncOfficial(),
                m.createdTime(), m.updatedTime());
    }
}
