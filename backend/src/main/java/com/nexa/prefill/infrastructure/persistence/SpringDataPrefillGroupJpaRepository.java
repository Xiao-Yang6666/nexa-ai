package com.nexa.prefill.infrastructure.persistence;

import com.nexa.prefill.infrastructure.persistence.entity.PrefillGroupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 预填分组 Spring Data JPA 仓库（基础设施层内部接口）。
 *
 * <p>仅供 {@link PrefillGroupRepositoryImpl} 内部使用，不暴露给应用/领域层——领域只认
 * {@code domain.repository.PrefillGroupRepository}。所有查询经 {@code @SQLRestriction}
 * 自动过滤软删除行（type/name 派生查询同理只命中存活行，名称冲突校验因此天然忽略已删组）。</p>
 */
interface SpringDataPrefillGroupJpaRepository extends JpaRepository<PrefillGroupJpaEntity, Long> {

    /**
     * 全量按 id 升序查（type 缺省返回全部，F-2014）。
     *
     * @return 全部存活分组（id 升序）
     */
    List<PrefillGroupJpaEntity> findAllByOrderByIdAsc();

    /**
     * 按 type 查（下拉填充指定类型，F-2014），id 升序。
     *
     * @param type 类型字面量（小写）
     * @return 该类型下存活分组（id 升序）
     */
    List<PrefillGroupJpaEntity> findByTypeOrderByIdAsc(String type);

    /**
     * 名称冲突探测：同 type 下是否存在指定 name 的存活分组（不含自身）。
     *
     * @param type 类型字面量
     * @param name 待校验名称
     * @param id   需排除的自身 id（创建时传一个不可能命中的值如 0 / -1）
     * @return 存在他组同名返回 {@code true}
     */
    boolean existsByTypeAndNameAndIdNot(String type, String name, Long id);

    /**
     * 名称冲突探测（创建场景，无自身需排除）。
     *
     * @param type 类型字面量
     * @param name 待校验名称
     * @return 存在同名返回 {@code true}
     */
    boolean existsByTypeAndName(String type, String name);

    /**
     * 按 id 查存活实体（更新/软删除定位）。
     *
     * @param id 主键
     * @return 命中存活实体，否则空
     */
    Optional<PrefillGroupJpaEntity> findById(Long id);
}
