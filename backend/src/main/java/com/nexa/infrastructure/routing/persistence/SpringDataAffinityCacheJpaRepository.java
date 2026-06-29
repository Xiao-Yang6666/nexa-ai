package com.nexa.infrastructure.routing.persistence;

import com.nexa.infrastructure.routing.persistence.po.AffinityCachePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 亲和缓存条目 Spring Data JPA 仓库（基础设施层细节，F-2029/F-2032/F-2033）。
 *
 * <p>由 {@link AffinityCacheRepositoryImpl} 适配为 domain 的
 * {@link com.nexa.domain.routing.repository.AffinityCacheRepository}。</p>
 */
@Repository
public interface SpringDataAffinityCacheJpaRepository extends JpaRepository<AffinityCachePO, Long> {

    /**
     * 按三元组（rule_name, key_fingerprint, using_group）查找一条缓存。
     *
     * <p>using_group 用 {@code IS NULL OR =} 兼容空值（PG 唯一索引用 COALESCE 表达式实现唯一）。</p>
     *
     * @param ruleName       规则名
     * @param keyFingerprint 会话键指纹
     * @param usingGroup     使用分组（可空）
     * @return 命中返回实体，否则空
     */
    @Query("SELECT c FROM AffinityCachePO c WHERE c.ruleName = :rule AND c.keyFingerprint = :fp "
            + "AND ((:grp IS NULL AND c.usingGroup IS NULL) OR c.usingGroup = :grp)")
    Optional<AffinityCachePO> findByTriplet(@Param("rule") String ruleName,
                                                   @Param("fp") String keyFingerprint,
                                                   @Param("grp") String usingGroup);

    /**
     * 全部清空（F-2032 all=true）。
     *
     * @return 删除条数
     */
    @Modifying
    @Query("DELETE FROM AffinityCachePO c")
    int deleteAllEntries();

    /**
     * 按规则名清空（F-2032 rule_name 指定）。
     *
     * @param ruleName 规则名
     * @return 删除条数
     */
    @Modifying
    @Query("DELETE FROM AffinityCachePO c WHERE c.ruleName = :rule")
    int deleteByRuleName(@Param("rule") String ruleName);
}
