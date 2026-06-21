package com.nexa.oauthprovider.infrastructure.persistence;

import com.nexa.oauthprovider.domain.exception.InvalidCustomOAuthProviderException;
import com.nexa.oauthprovider.domain.model.CustomOAuthProvider;
import com.nexa.oauthprovider.domain.repository.CustomOAuthProviderRepository;
import com.nexa.oauthprovider.domain.vo.OAuthEndpoints;
import com.nexa.oauthprovider.infrastructure.persistence.entity.CustomOAuthProviderJpaEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link CustomOAuthProviderRepository} 的 JPA 实现（基础设施层适配器，F-1024/1025）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataCustomOAuthProviderJpaRepository}
 * + 实体↔领域映射实现它。领域聚合 {@link CustomOAuthProvider} 与 JPA 实体
 * {@link CustomOAuthProviderJpaEntity} 分离，映射集中在此处，domain 因此不感知 Hibernate
 * （backend-engineer §2.3）。name 唯一索引冲突翻译为领域异常（不吞错）。</p>
 */
@Repository
public class CustomOAuthProviderRepositoryImpl implements CustomOAuthProviderRepository {

    private final SpringDataCustomOAuthProviderJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public CustomOAuthProviderRepositoryImpl(SpringDataCustomOAuthProviderJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public CustomOAuthProvider save(CustomOAuthProvider provider) {
        CustomOAuthProviderJpaEntity entity = toEntity(provider);
        try {
            CustomOAuthProviderJpaEntity saved = jpa.saveAndFlush(entity);
            provider.assignId(saved.getId());
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // name 唯一索引冲突：provider 重名，翻译为领域语义（不回显内部约束细节）。
            throw new InvalidCustomOAuthProviderException(
                    "custom oauth provider name already exists: " + provider.name());
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<CustomOAuthProvider> findById(long id) {
        return jpa.findById(id).map(CustomOAuthProviderRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<CustomOAuthProvider> findByName(String name) {
        return jpa.findByName(name).map(CustomOAuthProviderRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<CustomOAuthProvider> findAll() {
        return jpa.findAll().stream()
                .map(CustomOAuthProviderRepositoryImpl::toDomain)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    // ---- 领域聚合 <-> JPA 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param p 领域聚合
     * @return 待持久化的 JPA 实体
     */
    private static CustomOAuthProviderJpaEntity toEntity(CustomOAuthProvider p) {
        CustomOAuthProviderJpaEntity e = new CustomOAuthProviderJpaEntity();
        e.setId(p.id());
        e.setName(p.name());
        e.setClientId(p.clientId());
        e.setClientSecret(p.clientSecret());
        e.setAuthorizationEndpoint(p.endpoints().authorizationEndpoint());
        e.setTokenEndpoint(p.endpoints().tokenEndpoint());
        e.setUserinfoEndpoint(p.endpoints().userinfoEndpoint());
        e.setScopes(p.scopes());
        e.setEnabled(p.enabled());
        e.setCreatedAt(p.createdAt() == null ? Instant.now().getEpochSecond() : p.createdAt().getEpochSecond());
        e.setUpdatedAt(p.updatedAt() == null ? Instant.now().getEpochSecond() : p.updatedAt().getEpochSecond());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link CustomOAuthProvider#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的领域聚合
     */
    private static CustomOAuthProvider toDomain(CustomOAuthProviderJpaEntity e) {
        OAuthEndpoints endpoints = OAuthEndpoints.of(
                e.getAuthorizationEndpoint(), e.getTokenEndpoint(), e.getUserinfoEndpoint());
        Instant createdAt = e.getCreatedAt() == null ? Instant.now() : Instant.ofEpochSecond(e.getCreatedAt());
        Instant updatedAt = e.getUpdatedAt() == null ? createdAt : Instant.ofEpochSecond(e.getUpdatedAt());
        return CustomOAuthProvider.rehydrate(
                e.getId(), e.getName(), e.getClientId(), e.getClientSecret(),
                endpoints, e.getScopes(), e.isEnabled(), createdAt, updatedAt);
    }
}
