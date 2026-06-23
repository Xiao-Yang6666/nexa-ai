package com.nexa.model.domain.repository;

import com.nexa.model.domain.model.PlatformModelMapping;
import com.nexa.model.domain.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 超管底仓映射仓储接口（A→B，领域层定义，基础设施层实现，F-6002）。
 *
 * <p>DDD 依赖倒置（backend-engineer §2.3）。关联表：V12 {@code platform_model_mappings}。
 * <b>B 不可见三道闸</b>之数据层闸：本仓储无客户读路径，仅 admin/root 应用层用。</p>
 */
public interface PlatformModelMappingRepository {

    /**
     * 保存（新增或更新）映射。新建保存后返回携带自增 id 的聚合。
     *
     * @param mapping 待保存的映射聚合
     * @return 持久化后的映射（新建含 id）
     */
    PlatformModelMapping save(PlatformModelMapping mapping);

    /**
     * 按主键查映射。
     *
     * @param id 主键
     * @return 命中返回聚合，否则空
     */
    Optional<PlatformModelMapping> findById(long id);

    /**
     * 按对外名 A 查映射（幂等键查重，F-6002 1对1 保证）。
     *
     * @param publicName 对外名 A
     * @return 命中返回聚合，否则空
     */
    Optional<PlatformModelMapping> findByPublicName(String publicName);

    /**
     * 分页列表（F-6002，按 id 升序）。
     *
     * @param pagination 分页参数
     * @return 当前页映射列表
     */
    List<PlatformModelMapping> findPage(Pagination pagination);

    /** @return 映射总数 */
    long count();

    /**
     * 软删除映射（F-6002）。
     *
     * @param id 主键
     */
    void deleteById(long id);
}
