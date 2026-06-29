package com.nexa.domain.model.repository;

import com.nexa.domain.model.model.Vendor;
import com.nexa.domain.model.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 供应商元数据仓储接口（领域层定义，基础设施层实现，F-3018）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力（backend-engineer §2.3）。实现见
 * {@code infrastructure.persistence.VendorRepositoryImpl}。关联表：V10 {@code vendor_metas}。</p>
 */
public interface VendorRepository {

    /**
     * 保存（新增或更新）供应商。新建保存后返回携带自增 id 的聚合。
     *
     * @param vendor 待保存的供应商聚合
     * @return 持久化后的供应商（新建含 id）
     */
    Vendor save(Vendor vendor);

    /**
     * 按主键查供应商。
     *
     * @param id 主键
     * @return 命中返回聚合，否则空
     */
    Optional<Vendor> findById(long id);

    /**
     * 按名称查供应商（幂等键查重，F-3018/F-3019 同步 upsert）。
     *
     * @param name 供应商名
     * @return 命中返回聚合，否则空
     */
    Optional<Vendor> findByName(String name);

    /**
     * 分页列表（F-3018，按 id 升序）。
     *
     * @param pagination 分页参数
     * @return 当前页供应商列表
     */
    List<Vendor> findPage(Pagination pagination);

    /** @return 供应商总数（F-3018 列表 total） */
    long count();

    /**
     * 关键词搜索（F-3018，按名称匹配，全量不分页——供应商量级小）。
     *
     * @param keyword 关键词（可空白 → 全量）
     * @return 命中供应商列表
     */
    List<Vendor> search(String keyword);

    /**
     * 列出全部供应商（enrich 用）。
     *
     * @return 全部供应商列表
     */
    List<Vendor> findAll();

    /**
     * 软删除供应商（F-3018）。
     *
     * @param id 主键
     */
    void deleteById(long id);
}
