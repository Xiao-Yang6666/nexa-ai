package com.nexa.infrastructure.model.persistence;

import com.nexa.infrastructure.model.persistence.mapper.ModelMetaMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexa.infrastructure.persistence.PageQueries;

import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import com.nexa.domain.model.vo.Pagination;
import com.nexa.infrastructure.model.persistence.po.ModelMetaPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 领域仓储 {@link ModelMetaRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-3013~F-3021）。
 *
 * <p>DDD 依赖倒置落地：domain 定接口，本类用 {@link ModelMetaMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现。领域聚合 {@link ModelMeta} 与 {@link ModelMetaPO} 分离
 * （domain 不感知持久化框架，backend-engineer §2.3）。关键词搜索在本层归一为小写空白（搜索 SQL 不感知归一前的脏值）。
 * 软删除用 deleted_at，select 由 {@code @TableLogic} 自动过滤已删行。</p>
 */
@Repository
public class ModelMetaRepositoryImpl implements ModelMetaRepository {

    private final ModelMetaMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public ModelMetaRepositoryImpl(ModelMetaMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public ModelMeta save(ModelMeta model) {
        ModelMetaPO entity = ModelMetaPO.of(model);
        if (entity.getId() == null) {
            mapper.insert(entity);          // 回填自增 id
        } else {
            mapper.updateById(entity);
        }
        model.assignId(entity.getId());
        return entity.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelMeta> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(ModelMetaPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ModelMeta> findByModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<ModelMetaPO> w = Wrappers.<ModelMetaPO>lambdaQuery()
                .eq(ModelMetaPO::getModelName, modelName.trim());
        return Optional.ofNullable(mapper.selectOne(w)).map(ModelMetaPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelMeta> findPage(Pagination pagination) {
        Page<ModelMetaPO> page = PageQueries.mpOf(pagination.page(), pagination.pageSize());
        LambdaQueryWrapper<ModelMetaPO> w = Wrappers.<ModelMetaPO>lambdaQuery()
                .orderByAsc(ModelMetaPO::getId);
        return mapper.selectPage(page, w).getRecords().stream().map(ModelMetaPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        return mapper.selectCount(Wrappers.<ModelMetaPO>lambdaQuery());
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelMeta> search(String keyword, Long vendorId, Pagination pagination) {
        Page<ModelMetaPO> page = PageQueries.mpOf(pagination.page(), pagination.pageSize());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        LambdaQueryWrapper<ModelMetaPO> w = searchWrapper(kw, vendorId)
                .orderByAsc(ModelMetaPO::getId);
        return mapper.selectPage(page, w).getRecords().stream().map(ModelMetaPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countSearch(String keyword, Long vendorId) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return mapper.selectCount(searchWrapper(kw, vendorId));
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelMeta> findAll() {
        return mapper.selectList(Wrappers.<ModelMetaPO>lambdaQuery())
                .stream().map(ModelMetaPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        mapper.softDeleteById(id, Instant.now().getEpochSecond());
    }

    /** {@inheritDoc} */
    @Override
    public Map<Long, Long> countByVendor() {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : mapper.countGroupByVendor()) {
            // vendor_id 可空 Long（无供应商归属时为 null），cnt 为 COUNT(*) bigint。
            Object vid = row.get("vendor_id");
            Object cnt = row.get("cnt");
            Long vendorId = vid == null ? null : ((Number) vid).longValue();
            Long count = cnt == null ? 0L : ((Number) cnt).longValue();
            result.put(vendorId, count);
        }
        return result;
    }

    // ---- 多条件搜索 wrapper（关键词大小写不敏感包含 + 可选供应商过滤），search/countSearch 共用 ----

    /**
     * 组装搜索条件：可选 vendorId 等值过滤 + (model_name OR tags OR description) 大小写不敏感包含。
     *
     * <p>等价原 JPQL {@code (:vendorId IS NULL OR vendorId=:vendorId) AND (LOWER(modelName) LIKE %kw%
     * OR LOWER(COALESCE(tags,'')) LIKE %kw% OR LOWER(COALESCE(description,'')) LIKE %kw%)}。kw 已归一为小写
     * （空串 → LIKE %% 命中全部）；{0} 占位绑参防注入；COALESCE 兜底 null 列为空串。</p>
     */
    private LambdaQueryWrapper<ModelMetaPO> searchWrapper(String kw, Long vendorId) {
        String like = "%" + kw + "%";
        return Wrappers.<ModelMetaPO>lambdaQuery()
                .eq(vendorId != null, ModelMetaPO::getVendorId, vendorId)
                .and(q -> q.apply("LOWER(model_name) LIKE LOWER({0})", like)
                        .or().apply("LOWER(COALESCE(tags, '')) LIKE LOWER({0})", like)
                        .or().apply("LOWER(COALESCE(description, '')) LIKE LOWER({0})", like));
    }
}
