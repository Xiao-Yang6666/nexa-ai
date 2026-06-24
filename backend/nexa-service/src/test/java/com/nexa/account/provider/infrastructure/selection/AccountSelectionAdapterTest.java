package com.nexa.account.provider.infrastructure.selection;

import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import com.nexa.account.provider.domain.vo.AccountStatus;
import com.nexa.account.provider.domain.vo.Pagination;
import com.nexa.relay.domain.port.SelectedAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccountSelectionAdapter} 单测：group 选 account、优先级、platform 软对齐、排除重试、限流/过载回写。
 *
 * <p>用内存桩仓储走真实充血聚合(Account.create/markRateLimited 等)，不引入 Mockito 行为脚本。</p>
 */
@DisplayName("AccountSelectionAdapter 账号选择适配器")
class AccountSelectionAdapterTest {

    @Test
    @DisplayName("按 group 选可调度账号，priority 升序取最高优先(数小)")
    void selectsByPriorityAscending() {
        StubRepo repo = new StubRepo();
        repo.add(acc("low", "openai", 80, "g1"));
        repo.add(acc("high", "openai", 10, "g1"));
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(repo);

        SelectedAccount sel = adapter.selectAccount("g1", null, Set.of()).orElseThrow();
        assertEquals("{\"key\":\"high-cred\"}", sel.credentials(), "应选 priority=10 的高优先账号");
        assertEquals("openai", sel.platform());
    }

    @Test
    @DisplayName("platform 软对齐：优先选平台匹配的账号")
    void prefersPlatformMatch() {
        StubRepo repo = new StubRepo();
        repo.add(acc("openai-acc", "openai", 10, "g1")); // 更高优先但平台不匹配
        repo.add(acc("anthropic-acc", "anthropic", 50, "g1"));
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(repo);

        SelectedAccount sel = adapter.selectAccount("g1", "anthropic", Set.of()).orElseThrow();
        assertEquals("{\"key\":\"anthropic-acc-cred\"}", sel.credentials(), "平台匹配优先于纯优先级");
    }

    @Test
    @DisplayName("platform 无匹配 → 放宽约束回落到全池最高优先")
    void fallsBackWhenNoPlatformMatch() {
        StubRepo repo = new StubRepo();
        repo.add(acc("openai-acc", "openai", 10, "g1"));
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(repo);

        SelectedAccount sel = adapter.selectAccount("g1", "gemini", Set.of()).orElseThrow();
        assertEquals("{\"key\":\"openai-acc-cred\"}", sel.credentials(), "无 gemini 账号 → 放宽选 openai");
    }

    @Test
    @DisplayName("排除已尝试账号(重试)→ 取次优先")
    void excludesTriedAccounts() {
        StubRepo repo = new StubRepo();
        Account high = acc("high", "openai", 10, "g1");
        Account low = acc("low", "openai", 80, "g1");
        repo.add(high);
        repo.add(low);
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(repo);

        SelectedAccount sel = adapter.selectAccount("g1", null, Set.of(high.id())).orElseThrow();
        assertEquals("{\"key\":\"low-cred\"}", sel.credentials(), "排除 high 后取 low");
    }

    @Test
    @DisplayName("空 group / 无可用账号 → empty")
    void emptyWhenNoAccount() {
        StubRepo repo = new StubRepo();
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(repo);
        assertTrue(adapter.selectAccount("g1", null, Set.of()).isEmpty(), "无账号 → empty");
        assertTrue(adapter.selectAccount("  ", null, Set.of()).isEmpty(), "空 group → empty");
    }

    @Test
    @DisplayName("markRateLimited / markOverloaded 回写账号状态并持久化")
    void writesBackRateLimitAndOverload() {
        StubRepo repo = new StubRepo();
        Account a = acc("a", "openai", 10, "g1");
        repo.add(a);
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(repo);

        adapter.markRateLimited(a.id(), 9999L);
        assertEquals(AccountStatus.RATE_LIMITED, repo.findById(a.id()).orElseThrow().status());

        Account b = acc("b", "openai", 10, "g1");
        repo.add(b);
        long until = Instant.now().getEpochSecond() + 60;
        adapter.markOverloaded(b.id(), until);
        Account reloaded = repo.findById(b.id()).orElseThrow();
        assertEquals(until, reloaded.overloadUntil());
        assertFalse(reloaded.isSchedulable(Instant.now().getEpochSecond()), "过载窗内不可调度");
    }

    // ---- helpers ----

    private static Account acc(String name, String platform, int priority, String group) {
        return Account.create(name, platform, "api_key", "{\"key\":\"" + name + "-cred\"}",
                null, priority, null, null, null,
                List.of(com.nexa.account.provider.domain.vo.AccountGroupRef.of(group, 50)));
    }

    /** 内存桩仓储：仅实现 adapter 用到的方法，走真实充血聚合。 */
    static class StubRepo implements AccountRepository {
        private final Map<Long, Account> store = new LinkedHashMap<>();
        private long seq = 0;

        void add(Account a) {
            save(a);
        }

        @Override
        public Account save(Account a) {
            if (a.id() == null) {
                a.assignId(++seq);
            }
            store.put(a.id(), a);
            return a;
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Account> findSchedulableByGroup(String group, long now) {
            if (group == null || group.isBlank()) {
                return List.of();
            }
            List<Account> out = new ArrayList<>();
            for (Account a : store.values()) {
                boolean inGroup = a.groups().stream().anyMatch(g -> group.equals(g.group()));
                if (inGroup && a.isSchedulable(now)) {
                    out.add(a);
                }
            }
            out.sort(Comparator.comparingInt(Account::priority));
            return out;
        }

        @Override
        public List<Account> findPage(String platform, Pagination pagination) {
            return List.copyOf(store.values());
        }

        @Override
        public long count(String platform) {
            return store.size();
        }

        @Override
        public List<Account> findByPlatform(String platform) {
            return List.copyOf(store.values());
        }

        @Override
        public List<Account> findSchedulable(long now) {
            return store.values().stream().filter(a -> a.isSchedulable(now)).toList();
        }

        @Override
        public void deleteById(long id) {
            store.remove(id);
        }
    }
}
