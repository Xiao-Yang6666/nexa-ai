package com.nexa.infrastructure.growth.persistence;

import com.nexa.infrastructure.growth.persistence.entity.CheckinSettingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA 仓库（签到配置单行表，基础设施层内部接口）。
 *
 * <p>仅供 {@link CheckinSettingRepositoryImpl} 内部使用。表中至多一行（固定主键
 * {@link CheckinSettingJpaEntity#SINGLETON_ID}），用 {@code findById(1)} 读、{@code save} 覆盖写。</p>
 */
interface SpringDataCheckinSettingJpaRepository extends JpaRepository<CheckinSettingJpaEntity, Long> {
}
