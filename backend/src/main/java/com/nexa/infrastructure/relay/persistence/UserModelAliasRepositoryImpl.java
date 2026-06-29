package com.nexa.infrastructure.relay.persistence;

import com.nexa.domain.relay.model.UserModelAlias;
import com.nexa.domain.relay.repository.UserModelAliasRepository;
import com.nexa.domain.relay.vo.AliasScope;
import com.nexa.infrastructure.relay.persistence.po.UserModelAliasPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 客户层别名仓储 JPA 实现（基础设施层适配器，F-6011）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link SpringDataUserModelAliasRepository} + 实体↔领域映射实现。
 * scope 值对象 ↔ (scope_type, scope_id) 两列互转。</p>
 */
@Repository("relayUserModelAliasRepositoryImpl")
public class UserModelAliasRepositoryImpl implements UserModelAliasRepository {

    private final SpringDataUserModelAliasRepository jpa;

    public UserModelAliasRepositoryImpl(SpringDataUserModelAliasRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<String> findTargetByAlias(AliasScope scope, String alias) {
        return jpa.findEnabledByScopeAndAlias(scope.type().wire(), scope.id(), alias)
                .map(UserModelAliasPO::getTarget);
    }

    @Override
    public Optional<UserModelAlias> findById(Long id) {
        return jpa.findActiveById(id).map(this::toDomain);
    }

    @Override
    public List<UserModelAlias> findByScope(AliasScope scope) {
        return jpa.findByScope(scope.type().wire(), scope.id()).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(UserModelAlias alias) {
        UserModelAliasPO entity = alias.id() == null
                ? new UserModelAliasPO()
                : jpa.findById(alias.id()).orElseGet(UserModelAliasPO::new);
        entity.setScopeType(alias.scope().type().wire());
        entity.setScopeId(alias.scope().id());
        entity.setAlias(alias.alias());
        entity.setTarget(alias.target());
        entity.setEnabled(alias.isEnabled());
        entity.setCreatedTime(alias.createdTime());
        entity.setUpdatedTime(alias.updatedTime());
        jpa.save(entity);
    }

    @Override
    public void deleteById(Long id) {
        jpa.findActiveById(id).ifPresent(e -> {
            e.setDeletedAt(Instant.now().getEpochSecond());
            jpa.save(e);
        });
    }

    private UserModelAlias toDomain(UserModelAliasPO e) {
        AliasScope scope = new AliasScope(AliasScope.ScopeType.fromWire(e.getScopeType()), e.getScopeId());
        return UserModelAlias.builder()
                .id(e.getId())
                .scope(scope)
                .alias(e.getAlias())
                .target(e.getTarget())
                .enabled(e.isEnabled())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
