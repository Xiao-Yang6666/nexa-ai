package com.nexa.infrastructure.account.provider.registry;

import com.nexa.application.model.port.RefreshPricingPort;
import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import com.nexa.domain.model.vo.Pagination;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型登记适配器幂等性单测（方案 1 跨域写集成）。
 *
 * <p>用 in-memory 桩仓储验证：已存在的模型跳过、仅新建缺失的；空入参不动；新建语义为本地自建
 * （sync_official=0）；有新建才触发定价刷新。</p>
 */
class ModelRegistryAdapterTest {

    @Test
    void registersOnlyMissingModels_idempotent() {
        InMemoryModelMetaRepo repo = new InMemoryModelMetaRepo();
        // 预置一个已存在模型。
        repo.save(ModelMeta.create("gpt-4o", null, null, null, null, null, null));
        AtomicInteger refreshCount = new AtomicInteger();
        ModelRegistryAdapter adapter = new ModelRegistryAdapter(repo, refreshCount::incrementAndGet);

        // 探测到三个，其中 gpt-4o 已存在 → 只新建 2 个。
        int created = adapter.registerModelsIfAbsent(List.of("gpt-4o", "gpt-4o-mini", "o1"));

        assertThat(created).isEqualTo(2);
        assertThat(repo.allNames()).containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini", "o1");
        // 有新建 → 触发一次定价刷新。
        assertThat(refreshCount.get()).isEqualTo(1);

        // 再次登记同一批 → 幂等，零新建、零刷新。
        int createdAgain = adapter.registerModelsIfAbsent(List.of("gpt-4o", "gpt-4o-mini", "o1"));
        assertThat(createdAgain).isZero();
        assertThat(refreshCount.get()).isEqualTo(1);
    }

    @Test
    void newModelsAreLocalManaged_notOfficialSync() {
        InMemoryModelMetaRepo repo = new InMemoryModelMetaRepo();
        ModelRegistryAdapter adapter = new ModelRegistryAdapter(repo, () -> { });

        adapter.registerModelsIfAbsent(List.of("deepseek-chat"));

        ModelMeta saved = repo.findByModelName("deepseek-chat").orElseThrow();
        // 本地自建语义：后续官方同步覆盖时计入 skipped，不被改写。
        assertThat(saved.isLocalManaged()).isTrue();
        assertThat(saved.syncOfficial()).isEqualTo(ModelMeta.SYNC_LOCAL);
    }

    @Test
    void emptyOrNullInput_doesNothing() {
        InMemoryModelMetaRepo repo = new InMemoryModelMetaRepo();
        AtomicInteger refreshCount = new AtomicInteger();
        ModelRegistryAdapter adapter = new ModelRegistryAdapter(repo, refreshCount::incrementAndGet);

        assertThat(adapter.registerModelsIfAbsent(null)).isZero();
        assertThat(adapter.registerModelsIfAbsent(List.of())).isZero();
        assertThat(repo.allNames()).isEmpty();
        assertThat(refreshCount.get()).isZero();
    }

    @Test
    void blankNamesAreSkipped_andDedup() {
        InMemoryModelMetaRepo repo = new InMemoryModelMetaRepo();
        ModelRegistryAdapter adapter = new ModelRegistryAdapter(repo, () -> { });

        // 含空白、空串、重复 → 归一后只剩 gpt-4o 一个。
        int created = adapter.registerModelsIfAbsent(
                new ArrayList<>(List.of("  gpt-4o  ", "gpt-4o", "   ", "")));

        assertThat(created).isEqualTo(1);
        assertThat(repo.allNames()).containsExactly("gpt-4o");
    }

    /** In-memory 桩仓储（只实现适配器用到的方法，其余抛 UnsupportedOperationException）。 */
    private static final class InMemoryModelMetaRepo implements ModelMetaRepository {
        private final List<ModelMeta> store = new ArrayList<>();
        private long seq = 0;

        List<String> allNames() {
            return store.stream().map(ModelMeta::modelName).toList();
        }

        @Override
        public ModelMeta save(ModelMeta model) {
            if (model.id() == null) {
                model.assignId(++seq);
            }
            store.add(model);
            return model;
        }

        @Override
        public Optional<ModelMeta> findByModelName(String modelName) {
            return store.stream().filter(m -> m.modelName().equals(modelName)).findFirst();
        }

        @Override
        public Optional<ModelMeta> findById(long id) {
            return store.stream().filter(m -> m.id() != null && m.id() == id).findFirst();
        }

        @Override
        public List<ModelMeta> findAll() {
            return List.copyOf(store);
        }

        @Override
        public List<ModelMeta> findPage(Pagination pagination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public List<ModelMeta> search(String keyword, Long vendorId, Pagination pagination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countSearch(String keyword, Long vendorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Long, Long> countByVendor() {
            throw new UnsupportedOperationException();
        }
    }
}
