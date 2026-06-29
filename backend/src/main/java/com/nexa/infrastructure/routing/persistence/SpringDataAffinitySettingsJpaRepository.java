package com.nexa.infrastructure.routing.persistence;

import com.nexa.infrastructure.routing.persistence.po.AffinitySettingsPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 亲和缓存全局策略 Spring Data JPA 仓库（基础设施层细节，F-2031）。
 *
 * <p>表上 CHECK(id=1) 强制单例，调用方一律读写 id=1 行。</p>
 */
@Repository
public interface SpringDataAffinitySettingsJpaRepository extends JpaRepository<AffinitySettingsPO, Integer> {
}
