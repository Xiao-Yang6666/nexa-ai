package com.nexa.ops.infrastructure.persistence;

import com.nexa.ops.infrastructure.persistence.entity.SetupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA 仓库（系统初始化标记，基础设施层内部接口）。
 *
 * <p>仅供 {@link SetupRepositoryImpl} 内部使用。单行哨兵表，主键为 Integer（固定 1）。</p>
 */
interface SpringDataSetupJpaRepository extends JpaRepository<SetupJpaEntity, Integer> {
}
