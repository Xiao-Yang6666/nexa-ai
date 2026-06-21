package com.nexa.model.domain.vo;

/**
 * 上游模型同步执行结果计数值对象（F-3019）。
 *
 * <p>领域规则来源：PRD ML-2。同步执行后返回四类计数：新建模型数、新建供应商数、更新模型数、
 * 跳过模型数（sync_official=0 的本地自建模型不被覆盖，计入 skipped）。</p>
 *
 * <p>不可变值对象。提供 {@link #zero()} 表达「无缺失且无 overwrite 时不请求上游直接返回零计数」
 * （BACKLOG T-121 验收点）。</p>
 *
 * @param createdModels  新建模型数
 * @param createdVendors 新建供应商数
 * @param updatedModels  更新模型数
 * @param skippedModels  跳过模型数（本地自建不覆盖）
 */
public record ModelSyncResult(int createdModels,
                              int createdVendors,
                              int updatedModels,
                              int skippedModels) {

    /** @return 全零计数（无变更） */
    public static ModelSyncResult zero() {
        return new ModelSyncResult(0, 0, 0, 0);
    }
}
