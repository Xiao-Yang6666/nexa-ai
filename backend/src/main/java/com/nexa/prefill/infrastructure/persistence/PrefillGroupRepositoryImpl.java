package com.nexa.prefill.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.prefill.domain.exception.PrefillPersistenceException;
import com.nexa.prefill.domain.model.PrefillGroup;
import com.nexa.prefill.domain.repository.PrefillGroupRepository;
import com.nexa.prefill.domain.vo.PrefillItems;
import com.nexa.prefill.domain.vo.PrefillType;
import com.nexa.prefill.infrastructure.persistence.entity.PrefillGroupJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link PrefillGroupRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataPrefillGroupJpaRepository} +
 * 聚合↔实体映射实现（backend-engineer §2.3）。领域聚合 {@link PrefillGroup} 与 JPA 实体分离，
 * 映射集中于此，domain 不感知 Hibernate / Jackson。</p>
 *
 * <p>{@code items} 条目集合 ⇄ JSONB：本层用 {@link ObjectMapper} 在 {@link PrefillItems} 与 JSON
 * 字符串数组（DB-SCHEMA §17 {@code ["gpt-4o",...]}）间互转；序列化失败包装为
 * {@link PrefillPersistenceException}（不吞错）。软删除经
 * {@link #softDelete(long, long)} 写 {@code deleted_at} 时间戳，{@code @SQLRestriction} 保证后续
 * 查询自动过滤。</p>
 */
@Repository
public class PrefillGroupRepositoryImpl implements PrefillGroupRepository {

    private final SpringDataPrefillGroupJpaRepository jpa;
    private final ObjectMapper objectMapper;

    /**
     * @param jpa          Spring Data JPA 仓库（infra 内部依赖）
     * @param objectMapper Jackson 序列化器（Spring Boot 容器提供，复用全站配置）
     */
    public PrefillGroupRepositoryImpl(SpringDataPrefillGroupJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PrefillGroup> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<PrefillGroup> findByType(PrefillType type) {
        // type 为 null → 全部类型（openapi GET 的 type 缺省返回全部）。
        List<PrefillGroupJpaEntity> entities = (type == null)
                ? jpa.findAllByOrderByIdAsc()
                : jpa.findByTypeOrderByIdAsc(type.wireValue());
        return entities.stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByTypeAndName(PrefillType type, String name, Long excludeId) {
        if (excludeId == null) {
            return jpa.existsByTypeAndName(type.wireValue(), name);
        }
        return jpa.existsByTypeAndNameAndIdNot(type.wireValue(), name, excludeId);
    }

    /** {@inheritDoc} */
    @Override
    public PrefillGroup save(PrefillGroup group) {
        PrefillGroupJpaEntity saved = jpa.save(toEntity(group));
        group.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public boolean softDelete(long id, long nowEpochSec) {
        // 先按 id 取存活行（@SQLRestriction 已过滤已删），不存在 → false（应用层据此判 404）。
        Optional<PrefillGroupJpaEntity> existing = jpa.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        PrefillGroupJpaEntity entity = existing.get();
        // 写 deleted_at 时间戳软删（保留历史，openapi「不物理移除」）；save 触发 UPDATE。
        entity.setDeletedAt(nowEpochSec);
        jpa.save(entity);
        return true;
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param g 预填分组聚合
     * @return 待持久化的 JPA 实体
     */
    private PrefillGroupJpaEntity toEntity(PrefillGroup g) {
        PrefillGroupJpaEntity e = new PrefillGroupJpaEntity();
        e.setId(g.id());
        e.setName(g.name());
        e.setType(g.type().wireValue());
        e.setItems(serializeItems(g.items()));
        e.setDescription(g.description());
        e.setCreatedTime(g.createdTime());
        e.setUpdatedTime(g.updatedTime());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link PrefillGroup#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的预填分组聚合
     */
    private PrefillGroup toDomain(PrefillGroupJpaEntity e) {
        return PrefillGroup.builder()
                .id(e.getId())
                .name(e.getName())
                .type(PrefillType.fromWire(e.getType()))
                .items(deserializeItems(e.getItems()))
                .description(e.getDescription())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }

    /**
     * 条目集合 → JSON 字符串数组（落库）。空集合存 {@code "[]"}（DB-SCHEMA §17 JSON 数组语义）。
     *
     * @param items 条目集合
     * @return JSON 串
     * @throws PrefillPersistenceException 序列化失败（保留错误链）
     */
    private String serializeItems(PrefillItems items) {
        try {
            List<String> values = (items == null) ? List.of() : items.values();
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            // 不吞错：包装为领域持久化异常，保留底层 Jackson 错误链（backend-engineer §3.2）。
            throw new PrefillPersistenceException("serialize prefill items to JSON failed", ex);
        }
    }

    /**
     * JSON 字符串数组 → 条目集合（重建）。null/空串视为空集合。
     *
     * @param json JSONB 列原始串
     * @return 条目集合值对象
     * @throws PrefillPersistenceException 反序列化失败（保留错误链）
     */
    private PrefillItems deserializeItems(String json) {
        if (json == null || json.isBlank()) {
            return PrefillItems.EMPTY;
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return PrefillItems.of(values);
        } catch (Exception ex) {
            throw new PrefillPersistenceException("deserialize prefill items from JSON failed", ex);
        }
    }
}
