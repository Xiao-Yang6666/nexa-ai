package com.nexa.infrastructure.routing.persistence;

import com.nexa.infrastructure.routing.persistence.po.AffinityRulePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 亲和规则 Spring Data JPA 仓库（基础设施层细节，F-2031）。
 *
 * <p>由 {@link AffinityRuleRepositoryImpl} 适配为 domain 的
 * {@link com.nexa.domain.routing.repository.AffinityRuleRepository}。</p>
 */
@Repository
public interface SpringDataAffinityRuleJpaRepository extends JpaRepository<AffinityRulePO, Long> {

    /**
     * 按规则名查找。
     *
     * @param name 规则名
     * @return 命中返回实体，否则空
     */
    Optional<AffinityRulePO> findByName(String name);

    /**
     * 查询全部已启用规则（按 builtIn DESC, name ASC 稳定排序）。
     *
     * @return 已启用规则列表
     */
    @Query("SELECT r FROM AffinityRulePO r WHERE r.enabled = true ORDER BY r.builtIn DESC, r.name ASC")
    List<AffinityRulePO> findAllEnabled();

    /**
     * 查询全部规则（按 builtIn DESC, name ASC 稳定排序）。
     *
     * @return 全部规则列表
     */
    @Query("SELECT r FROM AffinityRulePO r ORDER BY r.builtIn DESC, r.name ASC")
    List<AffinityRulePO> findAllSorted();

    /**
     * 按规则名删除（自定义规则用）。
     *
     * @param name 规则名
     * @return 删除条数
     */
    @Modifying
    @Query("DELETE FROM AffinityRulePO r WHERE r.name = :name")
    int deleteByName(@Param("name") String name);
}
