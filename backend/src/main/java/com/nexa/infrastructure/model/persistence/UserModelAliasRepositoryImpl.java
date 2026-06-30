package com.nexa.infrastructure.model.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.model.model.UserModelAlias;
import com.nexa.domain.model.repository.UserModelAliasRepository;
import com.nexa.domain.model.vo.AliasScopeType;
import com.nexa.infrastructure.model.persistence.po.UserModelAliasPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link UserModelAliasRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-6003）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link UserModelAliasMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现。越权 self-scope 护栏在应用层落地，本类仅按
 * (scope_type, scope_id) 过滤。{@code select} 经 {@code @TableLogic} 自动过滤已软删行；软删写走
 * {@link UserModelAliasMapper#softDeleteById}。</p>
 */
@Repository("modelUserModelAliasRepositoryImpl")
public class UserModelAliasRepositoryImpl implements UserModelAliasRepository {

    private final UserModelAliasMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public UserModelAliasRepositoryImpl(UserModelAliasMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public UserModelAlias save(UserModelAlias alias) {
        UserModelAliasPO po = UserModelAliasPO.of(alias);
        if (po.getId() == null) {
            mapper.insert(po);              // 回填自增 id
        } else {
            mapper.updateById(po);
        }
        alias.assignId(po.getId());
        return po.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserModelAlias> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(UserModelAliasPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserModelAlias> findByScopeAndAlias(AliasScopeType scopeType, String scopeId, String aliasName) {
        if (scopeType == null || scopeId == null || aliasName == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserModelAliasPO> w = Wrappers.<UserModelAliasPO>lambdaQuery()
                .eq(UserModelAliasPO::getScopeType, scopeType.code())
                .eq(UserModelAliasPO::getScopeId, scopeId.trim())
                .eq(UserModelAliasPO::getAlias, aliasName.trim());
        return Optional.ofNullable(mapper.selectOne(w)).map(UserModelAliasPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<UserModelAlias> findByScope(AliasScopeType scopeType, String scopeId) {
        LambdaQueryWrapper<UserModelAliasPO> w = Wrappers.<UserModelAliasPO>lambdaQuery()
                .eq(UserModelAliasPO::getScopeType, scopeType.code())
                .eq(UserModelAliasPO::getScopeId, scopeId)
                .orderByAsc(UserModelAliasPO::getAlias);
        return mapper.selectList(w).stream().map(UserModelAliasPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<UserModelAlias> findByUserAndGroups(long userId, List<String> userGroups) {
        // group IN () 在部分 DB 上行为不确定，空集时塞入不可能命中的哨兵值，保证语义为「仅 user-scope」。
        List<String> groups = (userGroups == null || userGroups.isEmpty())
                ? List.of("__no_group__")
                : userGroups;
        String userScopeId = String.valueOf(userId);
        // (scope_type='user' AND scope_id=userId) OR (scope_type='group' AND scope_id IN groups)。
        // 整个 OR 组合用一层 .and(outer->...) 包裹，确保 @TableLogic 自动追加的 `deleted_at IS NULL`
        // 以 `... AND ( (A) OR (B) )` 形式整体 AND，避免 AND>OR 优先级把软删过滤泄漏（仅 A 受约束、B 漏删）。
        LambdaQueryWrapper<UserModelAliasPO> w = Wrappers.<UserModelAliasPO>lambdaQuery()
                .and(outer -> outer
                        .and(q -> q.eq(UserModelAliasPO::getScopeType, "user")
                                .eq(UserModelAliasPO::getScopeId, userScopeId))
                        .or(q -> q.eq(UserModelAliasPO::getScopeType, "group")
                                .in(UserModelAliasPO::getScopeId, groups)))
                .orderByAsc(UserModelAliasPO::getScopeType)
                .orderByAsc(UserModelAliasPO::getAlias);
        return mapper.selectList(w).stream().map(UserModelAliasPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        mapper.softDeleteById(id, Instant.now().getEpochSecond());
    }
}
