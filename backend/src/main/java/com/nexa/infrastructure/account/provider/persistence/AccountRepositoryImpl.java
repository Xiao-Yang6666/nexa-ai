package com.nexa.infrastructure.account.provider.persistence;

import com.nexa.common.persistence.PageQueries;

import com.nexa.domain.account.provider.model.Account;
import com.nexa.domain.account.provider.repository.AccountRepository;
import com.nexa.domain.account.provider.vo.AccountGroupRef;
import com.nexa.domain.account.provider.vo.Pagination;
import com.nexa.infrastructure.account.provider.persistence.po.AccountAbilityPO;
import com.nexa.infrastructure.account.provider.persistence.po.AccountGroupPO;
import com.nexa.infrastructure.account.provider.persistence.po.AccountPO;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link AccountRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataAccountJpaRepository}
 * + 账号-分组关联仓储 + 实体↔领域映射实现它。领域聚合 {@link Account} 与 JPA 实体分离，映射集中此处。
 * {@code account_groups} 关联在 save 时 fan-out、delete 时 fan-in（仿 channel→abilities）。</p>
 */
@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private final SpringDataAccountJpaRepository jpa;
    private final SpringDataAccountGroupJpaRepository groupJpa;
    private final SpringDataAccountAbilityJpaRepository abilityJpa;

    /**
     * @param jpa      账号 Spring Data JPA 仓库
     * @param groupJpa 账号-分组关联 Spring Data JPA 仓库
     * @param abilityJpa abilities 路由索引 JPA 仓库
     */
    public AccountRepositoryImpl(SpringDataAccountJpaRepository jpa,
                                 SpringDataAccountGroupJpaRepository groupJpa,
                                 SpringDataAccountAbilityJpaRepository abilityJpa) {
        this.jpa = jpa;
        this.groupJpa = groupJpa;
        this.abilityJpa = abilityJpa;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Account save(Account account) {
        AccountPO saved = jpa.save(toEntity(account));
        account.assignId(saved.getId());
        rebuildGroups(saved.getId(), account.groups());
        rebuildAbilities(saved.getId(), account);
        return toDomain(saved, account.groups());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Account> findById(long id) {
        return jpa.findById(id).map(e -> toDomain(e, loadGroups(e.getId())));
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findPage(String platform, Pagination pagination) {
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
        return jpa.findPage(normalizeFilter(platform), pageable).stream()
                .map(e -> toDomain(e, loadGroups(e.getId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count(String platform) {
        return jpa.countFiltered(normalizeFilter(platform));
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findByPlatform(String platform) {
        return jpa.findByPlatform(platform).stream()
                .map(e -> toDomain(e, loadGroups(e.getId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findAll() {
        return jpa.findAll().stream()
                .map(e -> toDomain(e, loadGroups(e.getId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Account> findSchedulable(long now) {
        // 先按 ACTIVE 初筛（DB 索引命中），再用领域聚合 isSchedulable 终判过期/过载窗。
        return jpa.findActive().stream()
                .map(e -> toDomain(e, loadGroups(e.getId())))
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
        return groupJpa.findByGroup(g).stream()
                .map(AccountGroupPO::getAccountId)
                .distinct()
                .map(jpa::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(e -> toDomain(e, loadGroups(e.getId())))
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
        // 1) abilities 按模型 LIKE 粗筛 ACTIVE 行 → accountId 去重；2) 装配聚合；
        // 3) 领域 supportsModel 精确包含（剔除 LIKE 子串误命中）+ isSchedulable 终判；
        // 4) priority 升序（小=高优先）。售价分组与调度解耦：此处不看 group。
        String like = "%" + m + "%";
        return abilityJpa.findActiveByModelLike(like).stream()
                .map(AccountAbilityPO::getAccountId)
                .distinct()
                .map(jpa::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(e -> toDomain(e, loadGroups(e.getId())))
                .filter(a -> a.supportsModel(m))
                .filter(a -> a.isSchedulable(now))
                .sorted(java.util.Comparator.comparingInt(Account::priority))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        groupJpa.deleteByAccountId(id);
        abilityJpa.deleteByAccountId(id);
        jpa.deleteById(id);
    }

    private static String normalizeFilter(String platform) {
        return (platform == null || platform.isBlank()) ? null : platform.trim();
    }

    // ---- 领域聚合 <-> JPA 实体映射（基础设施层内部，领域不可见） ----

    private AccountPO toEntity(Account a) {
        AccountPO e = new AccountPO();
        e.setId(a.id());
        e.setName(a.name());
        e.setPlatform(a.platform());
        e.setType(a.type());
        e.setCredentials(a.credentials() == null ? "{}" : a.credentials());
        e.setBaseUrl(a.baseUrl());
        e.setConcurrency(a.concurrency());
        e.setPriority(a.priority());
        e.setStatus(a.status().code());
        e.setRateLimitedAt(a.rateLimitedAt());
        e.setRateLimitResetAt(a.rateLimitResetAt());
        e.setOverloadUntil(a.overloadUntil());
        e.setExpiresAt(a.expiresAt());
        e.setAutoPauseOnExpired(a.autoPauseOnExpired());
        e.setRateMultiplier(a.rateMultiplier());
        e.setModelMapping(a.modelMapping());
        e.setWeight(a.weight());
        e.setTag(a.tag());
        e.setAutoBan(a.autoBan());
        e.setResponseTime(a.responseTime());
        e.setTestTime(a.testTime());
        e.setBalance(a.balance());
        e.setUsedQuota(a.usedQuota());
        e.setModels(a.models());
        e.setCreatedAt(a.createdTime());
        e.setUpdatedAt(a.updatedTime());
        return e;
    }

    private Account toDomain(AccountPO e, List<AccountGroupRef> groups) {
        return Account.rehydrate(
                e.getId(),
                e.getName(),
                e.getPlatform(),
                e.getType(),
                "{}".equals(e.getCredentials()) ? null : e.getCredentials(),
                e.getBaseUrl(),
                e.getConcurrency(),
                e.getPriority(),
                e.getStatus(),
                e.getRateLimitedAt(),
                e.getRateLimitResetAt(),
                e.getOverloadUntil(),
                e.getExpiresAt(),
                e.isAutoPauseOnExpired(),
                e.getRateMultiplier(),
                e.getModelMapping(),
                e.getWeight(),
                e.getTag(),
                e.isAutoBan(),
                e.getResponseTime(),
                e.getTestTime(),
                e.getBalance(),
                e.getUsedQuota(),
                e.getModels(),
                groups,
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private List<AccountGroupRef> loadGroups(Long accountId) {
        return groupJpa.findByAccountId(accountId).stream()
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
        groupJpa.deleteByAccountId(accountId);
        if (groups == null || groups.isEmpty()) {
            return;
        }
        List<AccountGroupPO> rows = new ArrayList<>(groups.size());
        for (AccountGroupRef ref : groups) {
            rows.add(new AccountGroupPO(accountId, ref.group(), ref.priority()));
        }
        groupJpa.saveAll(rows);
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
        abilityJpa.deleteByAccountId(accountId);
        if (account.groups() == null || account.groups().isEmpty()) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        List<AccountAbilityPO> rows = new ArrayList<>();
        for (AccountGroupRef ref : account.groups()) {
            rows.add(new AccountAbilityPO(
                    accountId,
                    ref.group(),
                    account.models(),
                    account.tag(),
                    account.status().code(),
                    now,
                    now
            ));
        }
        abilityJpa.saveAll(rows);
    }
}
