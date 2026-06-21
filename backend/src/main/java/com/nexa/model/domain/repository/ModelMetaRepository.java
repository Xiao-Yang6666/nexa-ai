package com.nexa.model.domain.repository;

import com.nexa.model.domain.model.ModelMeta;
import com.nexa.model.domain.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 模型元数据仓储接口（领域层定义，基础设施层实现，F-3013~F-3021）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，可在单测中用 mock 替换。实现见
 * {@code infrastructure.persistence.ModelMetaRepositoryImpl}。关联表：V10 {@code model_metas}。</p>
 */
public interface ModelMetaRepository {

    /**
     * 保存（新增或更新）模型元数据。新建（id 为 null）保存后返回携带自增 id 的聚合。
     *
     * @param model 待保存的模型聚合
     * @return 持久化后的模型（新建含 id）
     */
    ModelMeta save(ModelMeta model);

    /**
     * 按主键查模型。
     *
     * @param id 主键
     * @return 命中返回聚合，否则空
     */
    Optional<ModelMeta> findById(long id);

    /**
     * 按模型名查模型（幂等键查重，F-3015/F-3016/F-3019）。
     *
     * @param modelName 模型名
     * @return 命中返回聚合，否则空
     */
    Optional<ModelMeta> findByModelName(String modelName);

    /**
     * 分页列表（F-3013，按 id 升序）。
     *
     * @param pagination 分页参数
     * @return 当前页模型列表
     */
    List<ModelMeta> findPage(Pagination pagination);

    /** @return 模型总数（F-3013 列表 total） */
    long count();

    /**
     * 关键词 + 供应商过滤分页搜索（F-3014）。
     *
     * @param keyword    关键词（可空白 → 不按关键词过滤）
     * @param vendorId   供应商过滤（可空 → 不过滤）
     * @param pagination 分页参数
     * @return 当前页模型列表
     */
    List<ModelMeta> search(String keyword, Long vendorId, Pagination pagination);

    /**
     * 关键词 + 供应商过滤搜索计数（F-3014 total）。
     *
     * @param keyword  关键词（可空白）
     * @param vendorId 供应商过滤（可空）
     * @return 命中总数
     */
    long countSearch(String keyword, Long vendorId);

    /**
     * 列出全部模型（同步比对 F-3019/F-3020、缺失检测 F-3021 用）。
     *
     * @return 全部模型列表
     */
    List<ModelMeta> findAll();

    /**
     * 软删除模型（F-3017）。
     *
     * @param id 主键
     */
    void deleteById(long id);

    /**
     * 按供应商分组计数（F-3013 vendor_counts，enrich 避免 N+1）。
     *
     * <p>返回 vendor_id → 模型数 的映射（vendor_id 可能为 null，调用方按需归一为 "0" 等键）。</p>
     *
     * @return vendor_id（可空）→ 模型计数
     */
    java.util.Map<Long, Long> countByVendor();
}
