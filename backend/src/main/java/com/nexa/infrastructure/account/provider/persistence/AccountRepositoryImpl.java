package com.nexa.infrastructure.account.provider.persistence;

import com.nexa.infrastructure.account.provider.persistence.mapper.AccountMapper;

import com.nexa.infrastructure.account.provider.persistence.mapper.AccountGroupMapper;

import com.nexa.infrastructure.account.provider.persistence.mapper.AccountAbilityMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.account.provider.model.Account;
import com.nexa.domain.account.provider.repository.AccountRepository;
import com.nexa.domain.account.provider.vo.AccountGroupRef;
import com.nexa.domain.account.provider.vo.Pagination;
import com.nexa.infrastructure.account.provider.persistence.po.AccountAbilityPO;
import com.nexa.infrastructure.account.provider.persistence.po.AccountGroupPO;
import com.nexa.infrastructure.account.provider.persistence.po.AccountPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link AccountRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link AccountMapper} + {@link AccountGroupMapper}
 * + {@link AccountAbilityMapper} + PO 就近工厂映射实现它。领域聚合 {@link Account} 与 PO 分离。
 * {@code account_groups}/{@code abilities} 关联在 save 时 fan-out、delete 时 fan-in（仿 channel→abilities）。</p>
 */
@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountMapper mapper;
    private final AccountGroupMapper groupMapper;
    private final AccountAbilityMapper abilityMapper;

    /**
     * @param mapper        账号 Mapper
     * @param groupMapper   账号-分组关联 Mapper
     * @param abilityMapper abilities 路由索引 Mapper
     */
    public AccountRepositoryImpl(AccountMapper mapper,
                                 AccountGroupMapper groupMapper,
                                 AccountAbilityMapper abilityMapper) {
        this.mapper = mapper;
        this.groupMapper = groupMapper;
        this.abilityMapper = abilityMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Account save(Account account) {
        AccountPO po = AccountPO.of(account);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        account.assignId(po.getId());
        rebuildGroups(po.getId(), account.groups());
        rebuildAbilities(po.getId(), account);
        return po.toDomain(account.groups());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Account> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id))
                .map(e -> e.toDomain(loadGroups(e.getId())));
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findPage(String platform, Pagination pagination) {
        // platform 可空=不过滤；ORDER BY priority DESC, id ASC；限量取当前页（与原 JPQL + Pageable 等价）。
        String p = normalizeFilter(platform);
        int page = Math.max(1, pagination.page());
        int pageSize = Math.max(1, pagination.pageSize());
        int offset = (page - 1) * pageSize;
        LambdaQueryWrapper<AccountPO> w = Wrappers.<AccountPO>lambdaQuery()
                .eq(p != null, AccountPO::getPlatform, p)
                .orderByDesc(AccountPO::getPriority)
                .orderByAsc(AccountPO::getId)
                .last("LIMIT " + pageSize + " OFFSET " + offset);   // page/pageSize 已 clamp 为可信整数
        return mapper.selectList(w).stream()
                .map(e -> e.toDomain(loadGroups(e.getId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count(String platform) {
        String p = normalizeFilter(platform);
        return mapper.selectCount(Wrappers.<AccountPO>lambdaQuery()
                .eq(p != null, AccountPO::getPlatform, p));
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findByPlatform(String platform) {
        return mapper.selectList(Wrappers.<AccountPO>lambdaQuery()
                        .eq(AccountPO::getPlatform, platform)).stream()
                .map(e -> e.toDomain(loadGroups(e.getId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findAll() {
        return mapper.selectList(null).stream()
                .map(e -> e.toDomain(loadGroups(e.getId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findSchedulable(long now) {
        // 先按 active 初筛（DB 索引命中），再用领域聚合 isSchedulable 终判过期/过载窗。
        return findActivePos().stream()
                .map(e -> e.toDomain(loadGroups(e.getId())))
                .filter(a -> a.isSchedulable(now))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findSchedulableByGroup(String group, long now) {
        String g = normalizeFilter(group);
        if (g == null) {
            return List.of();
        }
        // 1) account_groups 按 group 反查账号 id（去重）；2) 装配聚合；3) 领域 isSchedulable 终判；
        // 4) 账号 priority 升序（小=高优先）排序，由选择适配层决定最终取哪个。
        return groupMapper.selectList(Wrappers.<AccountGroupPO>lambdaQuery()
                        .eq(AccountGroupPO::getGroup, g)).stream()
                .map(AccountGroupPO::getAccountId)
                .distinct()
                .map(mapper::selectById)
                .filter(java.util.Objects::nonNull)
                .map(e -> e.toDomain(loadGroups(e.getId())))
                .filter(a -> a.isSchedulable(now))
                .sorted(java.util.Comparator.comparingInt(Account::priority))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findSchedulableByModel(String model, long now) {
        String m = normalizeFilter(model);
        if (m == null) {
            return List.of();
        }
        // 1) abilities 按模型 LIKE 粗筛 active 行 → accountId 去重；2) 装配聚合；
        // 3) 领域 supportsModel 精确包含（剔除 LIKE 子串误命中）+ isSchedulable 终判；
        // 4) priority 升序（小=高优先）。售价分组与调度解耦：此处不看 group。
        LambdaQueryWrapper<AccountAbilityPO> w = Wrappers.<AccountAbilityPO>lambdaQuery()
                .eq(AccountAbilityPO::getStatus, "active")
                .like(AccountAbilityPO::getModels, m);   // MP 自动包裹 %m%，等价原 modelLike
        return abilityMapper.selectList(w).stream()
                .map(AccountAbilityPO::getAccountId)
                .distinct()
                .map(mapper::selectById)
                .filter(java.util.Objects::nonNull)
                .map(e -> e.toDomain(loadGroups(e.getId())))
                .filter(a -> a.supportsModel(m))
                .filter(a -> a.isSchedulable(now))
                .sorted(java.util.Comparator.comparingInt(Account::priority))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        groupMapper.delete(Wrappers.<AccountGroupPO>lambdaQuery().eq(AccountGroupPO::getAccountId, id));
        abilityMapper.delete(Wrappers.<AccountAbilityPO>lambdaQuery().eq(AccountAbilityPO::getAccountId, id));
        mapper.deleteById(id);
    }

    /** ACTIVE 态账号初筛（priority DESC, id ASC）——等价原 {@code findActive()} JPQL。 */
    private List<AccountPO> findActivePos() {
        return mapper.selectList(Wrappers.<AccountPO>lambdaQuery()
                .eq(AccountPO::getStatus, "active")
                .orderByDesc(AccountPO::getPriority)
                .orderByAsc(AccountPO::getId));
    }

    private static String normalizeFilter(String platform) {
        return (platform == null || platform.isBlank()) ? null : platform.trim();
    }

    private List<AccountGroupRef> loadGroups(Long accountId) {
        return groupMapper.selectList(Wrappers.<AccountGroupPO>lambdaQuery()
                        .eq(AccountGroupPO::getAccountId, accountId)).stream()
                .map(g -> new AccountGroupRef(g.getGroup(), g.getPriority()))
                .toList();
    }

    /**
     * 重建某账号的 account_groups 关联（save 后调用）：先按 accountId 全删，再 fan-out 插入。
     *
     * @param accountId 账号 id
     * @param groups    分组关联集合
     */
    private void rebuildGroups(Long accountId, List<AccountGroupRef> groups) {
        if (accountId == null) {
            return;
        }
        groupMapper.delete(Wrappers.<AccountGroupPO>lambdaQuery().eq(AccountGroupPO::getAccountId, accountId));
        if (groups == null || groups.isEmpty()) {
            return;
        }
        for (AccountGroupRef ref : groups) {
            groupMapper.insert(new AccountGroupPO(accountId, ref.group(), ref.priority()));
        }
    }

    /**
     * 重建某账号的 abilities 路由索引（save 后调用）：先按 accountId 全删，再 fan-out 插入。
     *
     * @param accountId 账号 id
     * @param account   账号聚合
     */
    private void rebuildAbilities(Long accountId, Account account) {
        if (accountId == null) {
            return;
        }
        abilityMapper.delete(Wrappers.<AccountAbilityPO>lambdaQuery().eq(AccountAbilityPO::getAccountId, accountId));
        if (account.groups() == null || account.groups().isEmpty()) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        for (AccountGroupRef ref : account.groups()) {
            abilityMapper.insert(new AccountAbilityPO(
                    accountId,
                    ref.group(),
                    account.models(),
                    account.tag(),
                    account.status().code(),
                    now,
                    now));
        }
    }
}
