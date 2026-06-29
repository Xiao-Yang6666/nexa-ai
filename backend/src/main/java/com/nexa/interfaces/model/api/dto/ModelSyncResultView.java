package com.nexa.interfaces.model.api.dto;

import com.nexa.domain.model.vo.ModelSyncResult;

/**
 * 上游模型同步执行结果视图（接口层出参，F-3019）。对齐 openapi {@code ModelSyncResult}。
 *
 * @param createdModels  新建模型数
 * @param createdVendors 新建供应商数
 * @param updatedModels  更新模型数
 * @param skippedModels  跳过模型数（本地自建不覆盖）
 */
public record ModelSyncResultView(int createdModels,
                                  int createdVendors,
                                  int updatedModels,
                                  int skippedModels) {

    /**
     * 由领域值对象裁剪为视图。
     *
     * @param r 同步结果值对象
     * @return 结果视图
     */
    public static ModelSyncResultView from(ModelSyncResult r) {
        return new ModelSyncResultView(r.createdModels(), r.createdVendors(),
                r.updatedModels(), r.skippedModels());
    }
}
