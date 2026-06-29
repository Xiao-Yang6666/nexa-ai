package com.nexa.infrastructure.modelgroup.pricing;

import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.repository.ModelGroupRepository;
import com.nexa.domain.modelgroup.vo.AccessPolicy;
import com.nexa.domain.modelgroup.vo.ModelGroupStatus;
import com.nexa.domain.modelgroup.vo.ModelNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelGroupPricingAdapter} 单测：分组 code → 售价倍率，启用过滤与回落语义。
 */
@DisplayName("ModelGroupPricingAdapter 模型组定价适配器")
class ModelGroupPricingAdapterTest {

    @Test
    @DisplayName("启用组 → 返回其 basePriceRatio")
    void enabledGroupReturnsRatio() {
        StubRepo repo = new StubRepo();
        repo.put("premium", new BigDecimal("1.5"), ModelGroupStatus.ENABLED);
        ModelGroupPricingAdapter adapter = new ModelGroupPricingAdapter(repo);

        assertEquals(new BigDecimal("1.5"), adapter.priceRatioOf("premium").orElseThrow());
        assertEquals(new BigDecimal("1.5"), adapter.priceRatioOf("  PREMIUM ").orElseThrow()); // 去空白+小写
    }

    @Test
    @DisplayName("禁用组 → empty（回落兜底）")
    void disabledGroupEmpty() {
        StubRepo repo = new StubRepo();
        repo.put("frozen", new BigDecimal("2.0"), ModelGroupStatus.DISABLED);
        ModelGroupPricingAdapter adapter = new ModelGroupPricingAdapter(repo);
        assertTrue(adapter.priceRatioOf("frozen").isEmpty());
    }

    @Test
    @DisplayName("无匹配 code / 空白 → empty")
    void missingOrBlankEmpty() {
        ModelGroupPricingAdapter adapter = new ModelGroupPricingAdapter(new StubRepo());
        assertTrue(adapter.priceRatioOf("not_exist").isEmpty());
        assertTrue(adapter.priceRatioOf("").isEmpty());
        assertTrue(adapter.priceRatioOf(null).isEmpty());
    }

    /** 仅实现 findByCode 的桩仓储（其余方法返回空，不被本测试触达）。 */
    private static final class StubRepo implements ModelGroupRepository {
        private final Map<String, ModelGroup> byCode = new HashMap<>();

        void put(String code, BigDecimal ratio, ModelGroupStatus status) {
            byCode.put(code, ModelGroup.builder()
                    .id(1L).name(code).code(code).basePriceRatio(ratio)
                    .models(ModelNames.of(List.of("gpt-4o"))).accessPolicy(AccessPolicy.PUBLIC)
                    .status(status).createdTime(1L).updatedTime(1L).build());
        }

        @Override
        public Optional<ModelGroup> findByCode(String code) {
            return Optional.ofNullable(byCode.get(code));
        }

        @Override public Optional<ModelGroup> findById(long id) { return Optional.empty(); }
        @Override public List<ModelGroup> findAll() { return List.of(); }
        @Override public List<ModelGroup> findByAccessPolicy(AccessPolicy p) { return List.of(); }
        @Override public List<ModelGroup> findByIds(List<Long> ids) { return List.of(); }
        @Override public boolean existsByCode(String code, Long excludeId) { return false; }
        @Override public ModelGroup save(ModelGroup g) { return g; }
        @Override public boolean softDelete(long id, long now) { return false; }
    }
}
