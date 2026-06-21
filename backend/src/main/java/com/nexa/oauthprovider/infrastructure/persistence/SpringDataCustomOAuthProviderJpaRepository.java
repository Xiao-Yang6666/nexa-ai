package com.nexa.oauthprovider.infrastructure.persistence;

import com.nexa.oauthprovider.infrastructure.persistence.entity.CustomOAuthProviderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA 仓库（自定义 OAuth provider，基础设施层内部接口）。
 *
 * <p>仅供 {@link CustomOAuthProviderRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.CustomOAuthProviderRepository}。按 name 唯一索引派生查询。</p>
 */
interface SpringDataCustomOAuthProviderJpaRepository
        extends JpaRepository<CustomOAuthProviderJpaEntity, Long> {

    /**
     * 按 name（路由标识）查 provider。
     *
     * @param name provider 路由标识
     * @return 命中实体，否则空（name 唯一，至多一条）
     */
    Optional<CustomOAuthProviderJpaEntity> findByName(String name);
}
