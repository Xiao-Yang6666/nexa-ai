package com.nexa.model.application;

import com.nexa.model.application.port.ChannelModelCatalog;
import com.nexa.model.application.port.RefreshPricingPort;
import com.nexa.model.application.port.UserGroupQuery;
import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.model.PublicModel;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.model.domain.vo.Pagination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「超管写 → 模型广场读」端到端闭环单测（纯内存桩仓储，不起 Spring/DB）。
 *
 * <p>验证 r6c 修复核心：超管经 {@link ManagePublicModelUseCase} 配置对外模型（上架/下架），
 * C 端可见模型列表 {@link ModelSquareUseCase#visibleModels} 与对外全集
 * {@link ListPublicModelsUseCase#listEnabledPublicNames} 立即反映——三者共享唯一权威源
 * {@link PublicModelRepository}（修复前 visibleModels 读 channel，与超管配置脱节）。</p>
 */
@DisplayName("模型广场超管写→广场读闭环")
class ModelSquareClosedLoopTest {

    private static final long USER_ID = 100L;

    private InMemoryPublicModelRepo repo;
    private ManagePublicModelUseCase admin;
    private ModelSquareUseCase square;
    private ListPublicModelsUseCase publicList;

    @BeforeEach
    void setUp() {
        repo = new InMemoryPublicModelRepo();
        admin = new ManagePublicModelUseCase(repo, new NoOpRefresh());
        // 用户分组查询：USER_ID 存在（返回任意分组），其余用户不存在。
        UserGroupQuery userGroupQuery = userId ->
                userId == USER_ID ? Optional.of("free") : Optional.empty();
        square = new ModelSquareUseCase(new EmptyChannelCatalog(), userGroupQuery, repo);
        publicList = new ListPublicModelsUseCase(repo);
    }

    @Test
    @DisplayName("超管上架模型 → 用户可见列表与对外全集均查得到")
    void adminEnableThenVisible() {
        admin.create("gpt-4o", BigDecimal.ONE, false, null, true, "GPT-4o", 0, null);

        assertTrue(square.visibleModels(USER_ID).contains("gpt-4o"));
        assertTrue(publicList.listEnabledPublicNames().contains("gpt-4o"));
    }

    @Test
    @DisplayName("超管下架模型 → 用户可见列表与对外全集均查不到")
    void adminDisableThenInvisible() {
        PublicModel created =
                admin.create("claude-opus", BigDecimal.ONE, false, null, true, "Opus", 0, null);
        assertTrue(square.visibleModels(USER_ID).contains("claude-opus"));

        // 下架（enabled=false）。
        admin.update(created.id(), null, null, null, false, null, null, null);

        assertFalse(square.visibleModels(USER_ID).contains("claude-opus"));
        assertFalse(publicList.listEnabledPublicNames().contains("claude-opus"));
    }

    @Test
    @DisplayName("超管软删模型 → 移出对外全集与可见列表")
    void adminDeleteThenInvisible() {
        PublicModel created =
                admin.create("gemini-pro", BigDecimal.ONE, false, null, true, "Gemini", 0, null);
        assertTrue(square.visibleModels(USER_ID).contains("gemini-pro"));

        admin.delete(created.id());

        assertFalse(square.visibleModels(USER_ID).contains("gemini-pro"));
        assertFalse(publicList.listEnabledPublicNames().contains("gemini-pro"));
    }

    @Test
    @DisplayName("可见列表为上架全集去重保序，下架项被剔除")
    void visibleListIsEnabledOnlyOrdered() {
        admin.create("model-a", BigDecimal.ONE, false, null, true, "A", 1, null);
        PublicModel b = admin.create("model-b", BigDecimal.ONE, false, null, true, "B", 2, null);
        admin.create("model-c", BigDecimal.ONE, false, null, true, "C", 3, null);
        admin.update(b.id(), null, null, null, false, null, null, null); // 下架 b

        assertEquals(List.of("model-a", "model-c"), square.visibleModels(USER_ID));
    }

    @Test
    @DisplayName("用户不存在 → 抛 InvalidModelParameterException")
    void unknownUserRejected() {
        assertThrows(InvalidModelParameterException.class, () -> square.visibleModels(999L));
    }

    // ===== 桩 =====

    private static final class NoOpRefresh implements RefreshPricingPort {
        @Override
        public void refresh() {
            // no-op
        }
    }

    /** dashboard 用，闭环测试无需渠道数据。 */
    private static final class EmptyChannelCatalog implements ChannelModelCatalog {
        @Override
        public List<String> referencedModelNames() {
            return List.of();
        }

        @Override
        public Map<Long, List<String>> channelIdToModels() {
            return Map.of();
        }
    }

    /** 对外模型内存仓储：复刻 enabled 过滤 + 软删 + sort_order 升序语义。 */
    private static final class InMemoryPublicModelRepo implements PublicModelRepository {
        private final Map<Long, PublicModel> store = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong(1);

        @Override
        public PublicModel save(PublicModel model) {
            if (model.id() == null) {
                model.assignId(seq.getAndIncrement());
            }
            store.put(model.id(), model);
            return model;
        }

        @Override
        public Optional<PublicModel> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<PublicModel> findByPublicName(String publicName) {
            return store.values().stream()
                    .filter(m -> m.publicName().equals(publicName))
                    .findFirst();
        }

        @Override
        public List<PublicModel> findPage(Pagination pagination, boolean enabledOnly) {
            return enabledOnly ? enabledOrdered() : new ArrayList<>(store.values());
        }

        @Override
        public long count(boolean enabledOnly) {
            return enabledOnly ? enabledOrdered().size() : store.size();
        }

        @Override
        public List<String> findEnabledNames() {
            return enabledOrdered().stream().map(PublicModel::publicName).toList();
        }

        @Override
        public List<PublicModel> findAllEnabled() {
            return enabledOrdered();
        }

        @Override
        public void deleteById(long id) {
            // 软删语义：移出可查全集（内存桩等价直接 remove）。
            store.remove(id);
        }

        private List<PublicModel> enabledOrdered() {
            return store.values().stream()
                    .filter(m -> Boolean.TRUE.equals(m.enabled()))
                    .sorted((x, y) -> {
                        int s = Integer.compare(x.sortOrder(), y.sortOrder());
                        return s != 0 ? s : Long.compare(x.id(), y.id());
                    })
                    .toList();
        }
    }
}
