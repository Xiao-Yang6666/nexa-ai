package com.nexa.infrastructure.relay.persistence;

import com.nexa.infrastructure.relay.persistence.mapper.UserModelAliasMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.relay.model.UserModelAlias;
import com.nexa.domain.relay.repository.UserModelAliasRepository;
import com.nexa.domain.relay.vo.AliasScope;
import com.nexa.infrastructure.relay.persistence.po.UserModelAliasPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 客户层别名仓储 {@link UserModelAliasRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-6011）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link UserModelAliasMapper} + PO 就近工厂方法
 * （{@link UserModelAliasPO#of} / {@code po.toDomain}）实现。scope 值对象 ↔ (scope_type, scope_id)
 * 两列互转。软删除：select 由 PO 上 {@code @TableLogic(value="null")} 自动过滤 {@code deleted_at IS NULL}；
 * 删除写走 {@link UserModelAliasMapper#softDeleteById}（不用 {@code deleteById}）。</p>
 */
@Repository("relayUserModelAliasRepositoryImpl")
public class UserModelAliasRepositoryImpl implements UserModelAliasRepository {

    private final UserModelAliasMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public UserModelAliasRepositoryImpl(UserModelAliasMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> findTargetByAlias(AliasScope scope, String alias) {
        // 原 JPQL: scopeType + scopeId + alias + enabled=true + deletedAt IS NULL（后者由 @TableLogic 自动追加）
        LambdaQueryWrapper<UserModelAliasPO> w = Wrappers.<UserModelAliasPO>lambdaQuery()
                .eq(UserModelAliasPO::getScopeType, scope.type().wire())
                .eq(UserModelAliasPO::getScopeId, scope.id())
                .eq(UserModelAliasPO::getAlias, alias)
                .eq(UserModelAliasPO::isEnabled, true);
        return Optional.ofNullable(mapper.selectOne(w)).map(UserModelAliasPO::getTarget);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserModelAlias> findById(Long id) {
        // 原 findActiveById：id + deletedAt IS NULL（后者由 @TableLogic 自动追加）
        return Optional.ofNullable(mapper.selectById(id)).map(UserModelAliasPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<UserModelAlias> findByScope(AliasScope scope) {
        // 原 JPQL: scopeType + scopeId + deletedAt IS NULL ORDER BY alias ASC
        LambdaQueryWrapper<UserModelAliasPO> w = Wrappers.<UserModelAliasPO>lambdaQuery()
                .eq(UserModelAliasPO::getScopeType, scope.type().wire())
                .eq(UserModelAliasPO::getScopeId, scope.id())
                .orderByAsc(UserModelAliasPO::getAlias);
        return mapper.selectList(w).stream().map(UserModelAliasPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public void save(UserModelAlias alias) {
        UserModelAliasPO po = UserModelAliasPO.of(alias);
        if (alias.id() == null) {
            mapper.insert(po);
        } else if (mapper.selectById(alias.id()) != null) {
            mapper.updateById(po);
        } else {
            // id 已给但当前无活跃行（与原 findById.orElseGet(new)+save 语义对齐：插入新行，自增 id）
            po.setId(null);
            mapper.insert(po);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteById(Long id) {
        // 软删：置 deleted_at（WHERE deleted_at IS NULL 保证仅活跃行受影响、幂等），与原 1:1
        mapper.softDeleteById(id, Instant.now().getEpochSecond());
    }
}
