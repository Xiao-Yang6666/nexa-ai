package com.nexa.infrastructure.model.persistence;

import com.nexa.domain.model.model.UserModelAlias;
import com.nexa.domain.model.repository.UserModelAliasRepository;
import com.nexa.domain.model.vo.AliasScopeType;
import com.nexa.infrastructure.model.persistence.entity.UserModelAliasJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link UserModelAliasRepository} 的 JPA 实现（基础设施层适配器，F-6003）。
 *
 * <p>DDD 依赖倒置落地。越权 self-scope 护栏在应用层落地，本类仅按 (scope_type, scope_id) 过滤。
 * 软删除用 deleted_at。</p>
 */
@Repository("modelUserModelAliasRepositoryImpl")
public class UserModelAliasRepositoryImpl implements UserModelAliasRepository {

    private final SpringDataUserModelAliasJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public UserModelAliasRepositoryImpl(SpringDataUserModelAliasJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public UserModelAlias save(UserModelAlias alias) {
        UserModelAliasJpaEntity saved = jpa.save(toEntity(alias));
        alias.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserModelAlias> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserModelAlias> findByScopeAndAlias(AliasScopeType scopeType, String scopeId, String aliasName) {
        if (scopeType == null || scopeId == null || aliasName == null) {
            return Optional.empty();
        }
        return jpa.findByScopeTypeAndScopeIdAndAlias(scopeType.code(), scopeId.trim(), aliasName.trim())
                .map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<UserModelAlias> findByScope(AliasScopeType scopeType, String scopeId) {
        return jpa.findByScopeTypeAndScopeId(scopeType.code(), scopeId).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<UserModelAlias> findByUserAndGroups(long userId, List<String> userGroups) {
        // group IN () 在部分 DB/JPQL 实现上行为不确定，空集时塞入不可能命中的哨兵值，保证语义为「仅 user-scope」。
        List<String> groups = (userGroups == null || userGroups.isEmpty())
                ? List.of("__no_group__")
                : userGroups;
        return jpa.findByUserAndGroups(String.valueOf(userId), groups).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    // ---- 领域聚合 <-> JPA 实体映射 ----

    private UserModelAliasJpaEntity toEntity(UserModelAlias a) {
        UserModelAliasJpaEntity e = new UserModelAliasJpaEntity();
        e.setId(a.id());
        e.setScopeType(a.scopeType().code());
        e.setScopeId(a.scopeId());
        e.setAlias(a.alias());
        e.setTarget(a.target());
        e.setEnabled(a.enabled());
        e.setCreatedTime(a.createdTime());
        e.setUpdatedTime(a.updatedTime());
        return e;
    }

    private UserModelAlias toDomain(UserModelAliasJpaEntity e) {
        return UserModelAlias.builder()
                .id(e.getId())
                .scopeType(AliasScopeType.fromCode(e.getScopeType()))
                .scopeId(e.getScopeId())
                .alias(e.getAlias())
                .target(e.getTarget())
                .enabled(e.getEnabled())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
