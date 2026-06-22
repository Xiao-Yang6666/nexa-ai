package com.nexa.prefill.domain.repository;

import com.nexa.prefill.domain.model.PrefillGroup;
import com.nexa.prefill.domain.vo.PrefillType;

import java.util.List;
import java.util.Optional;

/**
 * 预填分组聚合仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，单测可桩替换。实现见
 * {@code infrastructure.persistence.PrefillGroupRepositoryImpl}。软删除行由实现层
 * {@code @SQLRestriction} 自动过滤，本接口语义上只见「存活」分组。</p>
 */
public interface PrefillGroupRepository {

    /**
     * 按 id 查找预填分组（更新/软删除前定位，F-2013/F-2015）。
     *
     * @param id 分组主键
     * @return 命中返回聚合，否则空（空 → 应用层抛 PrefillGroupNotFoundException）
     */
    Optional<PrefillGroup> findById(long id);

    /**
     * 按类型查询同 type 下所有存活分组（下拉填充，F-2014）。
     *
     * <p>{@code type} 为 null 时返回全部类型（openapi {@code GET /api/prefill_group} 的 type
     * 缺省返回全部）。结果按 id 升序，供下拉稳定展示。</p>
     *
     * @param type 分组类型（null = 全部）
     * @return 匹配的分组列表（可空集合）
     */
    List<PrefillGroup> findByType(PrefillType type);

    /**
     * 名称冲突探测（名称冲突校验，F-2012/F-2013）。
     *
     * <p>在同一 type 维度下，是否存在 name 相同且 id 不等于 {@code excludeId} 的存活分组。
     * 创建时 {@code excludeId} 传 {@code null}（无自身需排除）；更新时传被更新分组自身 id
     * （改回同名属合法，不算冲突）。</p>
     *
     * @param type      分组类型
     * @param name      待校验名称
     * @param excludeId 需排除的自身 id（null = 不排除，用于创建）
     * @return 存在他组同名返回 {@code true}
     */
    boolean existsByTypeAndName(PrefillType type, String name, Long excludeId);

    /**
     * 持久化预填分组聚合（新增或更新）。
     *
     * <p>新建保存后回填自增 id；更新已存在行（按 id）。</p>
     *
     * @param group 待保存的聚合
     * @return 持久化后的聚合（含 id）
     */
    PrefillGroup save(PrefillGroup group);

    /**
     * 软删除预填分组（F-2015，对齐 DB-SCHEMA §17 {@code deleted_at}）。
     *
     * <p>不物理删除，写 {@code deleted_at} 时间戳保留历史（openapi 描述「保留历史不物理移除」）。
     * id 不存在/已删除时返回 {@code false}，应用层据此判定 404。</p>
     *
     * @param id          分组主键
     * @param nowEpochSec 删除时间（epoch 秒，写入 deleted_at）
     * @return 命中并成功软删返回 {@code true}；id 不存在/已删返回 {@code false}
     */
    boolean softDelete(long id, long nowEpochSec);
}
