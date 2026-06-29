package com.nexa.model.infrastructure.catalog;

import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import com.nexa.account.provider.domain.vo.Pagination;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChannelModelCatalogAdapter} 单元测试（channel→account 数据源迁移后）。
 *
 * <p>验证模型目录端口从供应商账号 {@code Account.models}（逗号分隔串）正确投影：
 * referencedModelNames 去重保序、channelIdToModels 以 accountId 为键拆分模型名。
 * 用内存版 {@link AccountRepository} 假实现隔离 DB。</p>
 */
class ChannelModelCatalogAdapterTest {

    /** 构造账号：仅设置 id + models（其余字段对本测试无关，给最小合法值）。 */
    private static Account account(long id, String models) {
        return Account.rehydrate(id, "acc" + id, "openai", "api_key",
                "{\"key\":\"k\"}", null, 0, 0,
                "active", null, null, null, null, true,
                BigDecimal.ONE, null, 0, null, false,
                null, null, null, null, models, List.of(), null, null);
    }

    @Test
    void referencedModelNamesDedupAcrossAccounts() {
        StubAccountRepo repo = new StubAccountRepo(List.of(
                account(1L, "gpt-4o, gpt-4o-mini"),
                account(2L, "gpt-4o,claude-3-opus"),
                account(3L, "  ") // 空 models 不贡献
        ));
        ChannelModelCatalogAdapter adapter = new ChannelModelCatalogAdapter(repo);

        List<String> names = adapter.referencedModelNames();

        // 去重保序：gpt-4o 只出现一次，顺序按首次出现。
        assertEquals(List.of("gpt-4o", "gpt-4o-mini", "claude-3-opus"), names);
    }

    @Test
    void channelIdToModelsKeyedByAccountId() {
        StubAccountRepo repo = new StubAccountRepo(List.of(
                account(10L, "gpt-4o, gpt-4o-mini"),
                account(20L, "claude-3-opus")
        ));
        ChannelModelCatalogAdapter adapter = new ChannelModelCatalogAdapter(repo);

        Map<Long, List<String>> map = adapter.channelIdToModels();

        assertEquals(2, map.size());
        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), map.get(10L));
        assertEquals(List.of("claude-3-opus"), map.get(20L));
    }

    @Test
    void emptyModelsYieldEmptyProjection() {
        StubAccountRepo repo = new StubAccountRepo(List.of(account(1L, null)));
        ChannelModelCatalogAdapter adapter = new ChannelModelCatalogAdapter(repo);

        assertTrue(adapter.referencedModelNames().isEmpty());
        assertEquals(List.of(), adapter.channelIdToModels().get(1L));
    }

    /** 内存版账号仓储假实现：只支持 findAll（本 adapter 仅用它），其余方法最小桩。 */
    private static final class StubAccountRepo implements AccountRepository {
        private final List<Account> store;

        StubAccountRepo(List<Account> accounts) {
            this.store = new ArrayList<>(accounts);
        }

        @Override
        public List<Account> findAll() {
            return List.copyOf(store);
        }

        @Override public Account save(Account account) { throw new UnsupportedOperationException(); }
        @Override public Optional<Account> findById(long id) {
            return store.stream().filter(a -> a.id() != null && a.id() == id).findFirst();
        }
        @Override public List<Account> findPage(String platform, Pagination pagination) { return List.of(); }
        @Override public long count(String platform) { return store.size(); }
        @Override public List<Account> findByPlatform(String platform) { return List.of(); }
        @Override public List<Account> findSchedulable(long now) { return List.of(); }
        @Override public List<Account> findSchedulableByGroup(String group, long now) { return List.of(); }
        @Override public List<Account> findSchedulableByModel(String model, long now) { return List.of(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }
}
