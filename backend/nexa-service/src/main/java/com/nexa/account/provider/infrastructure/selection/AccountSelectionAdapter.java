package com.nexa.account.provider.infrastructure.selection;

import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import com.nexa.relay.domain.port.AccountSelectionPort;
import com.nexa.relay.domain.port.SelectedAccount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 账号选择端口适配器（account.provider BC 实现 relay 域定义的 {@link AccountSelectionPort}）。
 *
 * <p>依赖倒置落地：relay 域只依赖 {@code AccountSelectionPort} 接口，本适配器在 account BC 内用
 * {@link AccountRepository#findSchedulableByGroup} 选账号、裁剪为 {@link SelectedAccount} 投影回传，
 * 并承接转发失败时的限流/过载状态回写。relay 不编译期耦合 account 聚合内部。</p>
 *
 * <p>选择策略(原汁 sub2api 的 group 汇合)：
 * <ol>
 *   <li>按 group 取可调度账号池(已由仓储做 isSchedulable 终判 + priority 升序排)；</li>
 *   <li>platform 软对齐：先在平台匹配的子集里选；子集空则放宽到全池(避免无账号可用)；</li>
 *   <li>排除本次已尝试的 accountId(重试)，取首个(最高优先级)。</li>
 * </ol></p>
 */
@Component
public class AccountSelectionAdapter implements AccountSelectionPort {

    private final AccountRepository accountRepository;

    /**
     * @param accountRepository 供应商账号仓储
     */
    public AccountSelectionAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<SelectedAccount> selectAccount(String group, String platform, Set<Long> excludeAccountIds) {
        if (group == null || group.isBlank()) {
            return Optional.empty();
        }
        Set<Long> excluded = excludeAccountIds == null ? Set.of() : excludeAccountIds;
        // 仓储已按 priority 升序(小=高优先)返回可调度账号池。
        List<Account> pool = accountRepository.findSchedulableByGroup(group.trim(), now());

        // platform 软对齐：先在平台匹配子集选；空则放宽到全池。
        String wantPlatform = (platform == null || platform.isBlank()) ? null : platform.trim();
        Optional<Account> picked = pickFirst(pool, excluded, wantPlatform);
        if (picked.isEmpty() && wantPlatform != null) {
            picked = pickFirst(pool, excluded, null); // 放宽平台约束再试。
        }
        return picked.map(this::toSelected);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markRateLimited(long accountId, Long resetAt) {
        accountRepository.findById(accountId).ifPresent(a -> {
            a.markRateLimited(resetAt);
            accountRepository.save(a);
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markOverloaded(long accountId, Long until) {
        accountRepository.findById(accountId).ifPresent(a -> {
            a.markOverloaded(until);
            accountRepository.save(a);
        });
    }

    /**
     * 从池中按优先级顺序取首个：未被排除、且(平台不约束 或 平台匹配)的账号。
     *
     * @param pool         已按优先级升序的可调度账号池
     * @param excluded     需排除的 accountId
     * @param wantPlatform 期望平台(null=不约束)
     * @return 首个命中账号
     */
    private Optional<Account> pickFirst(List<Account> pool, Set<Long> excluded, String wantPlatform) {
        return pool.stream()
                .filter(a -> a.id() != null && !excluded.contains(a.id()))
                .filter(a -> wantPlatform == null || wantPlatform.equalsIgnoreCase(a.platform()))
                .findFirst();
    }

    private SelectedAccount toSelected(Account a) {
        return new SelectedAccount(
                a.id(), a.credentials(), a.baseUrl(), a.platform(), a.rateMultiplier(),
                a.modelMapping(), a.models(), a.tag(), a.weight());
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }
}
