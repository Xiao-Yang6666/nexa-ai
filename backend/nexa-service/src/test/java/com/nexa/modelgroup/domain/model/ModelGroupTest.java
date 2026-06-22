package com.nexa.modelgroup.domain.model;

import com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import com.nexa.modelgroup.domain.vo.ModelGroupStatus;
import com.nexa.modelgroup.domain.vo.ModelNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelGroup} 模型组聚合根单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖创建校验（name/code/倍率/策略）、code 规范化与格式校验、充血更新（部分更新 + 倍率/策略覆盖）、
 * 状态切换、可选用判定（启用 + 模型集非空）、模型归属判定——按正常/边界/异常组织。</p>
 */
@DisplayName("ModelGroup 模型组聚合根")
class ModelGroupTest {

    private static final long NOW = 1_700_000_000L;

    @Nested
    @DisplayName("create 创建工厂")
    class Create {

        @Test
        @DisplayName("合法入参 → 规范化、缺省启用、时间戳、id 为 null")
        void validCreate() {
            ModelGroup g = ModelGroup.create(
                    "  高级组 ", "  PREMIUM ", new BigDecimal("1.5"),
                    ModelNames.of(List.of("gpt-4o", "gpt-4o", " claude-3-opus ")),
                    AccessPolicy.PRIVATE, "  desc ", NOW);

            assertNull(g.id());
            assertEquals("高级组", g.name());
            assertEquals("premium", g.code()); // 去空白 + 小写
            assertEquals(new BigDecimal("1.5"), g.basePriceRatio().value());
            assertEquals(List.of("gpt-4o", "claude-3-opus"), g.models().values()); // 去重去空白
            assertEquals(AccessPolicy.PRIVATE, g.accessPolicy());
            assertEquals(ModelGroupStatus.ENABLED, g.status());
            assertEquals("desc", g.description());
            assertEquals(NOW, g.createdTime());
            assertEquals(NOW, g.updatedTime());
        }

        @Test
        @DisplayName("倍率为 null → 归一为 1.0；models 为 null → 空集")
        void nullDefaults() {
            ModelGroup g = ModelGroup.create("n", "basic", null, null, AccessPolicy.PUBLIC, null, NOW);
            assertEquals(BigDecimal.ONE, g.basePriceRatio().value());
            assertTrue(g.models().isEmpty());
            assertNull(g.description());
        }

        @Test
        @DisplayName("name 空白 → 抛异常")
        void blankNameRejected() {
            assertThrows(InvalidModelGroupParameterException.class,
                    () -> ModelGroup.create("  ", "basic", null, null, AccessPolicy.PUBLIC, null, NOW));
        }

        @Test
        @DisplayName("code 含非法字符 → 抛异常")
        void invalidCodeRejected() {
            assertThrows(InvalidModelGroupParameterException.class,
                    () -> ModelGroup.create("n", "Pre Mium!", null, null, AccessPolicy.PUBLIC, null, NOW));
        }

        @Test
        @DisplayName("负倍率 → 抛异常（复用 Ratio 不变量）")
        void negativeRatioRejected() {
            assertThrows(RuntimeException.class,
                    () -> ModelGroup.create("n", "basic", new BigDecimal("-1"),
                            null, AccessPolicy.PUBLIC, null, NOW));
        }

        @Test
        @DisplayName("访问策略为 null → 抛异常")
        void nullPolicyRejected() {
            assertThrows(InvalidModelGroupParameterException.class,
                    () -> ModelGroup.create("n", "basic", null, null, null, null, NOW));
        }
    }

    @Nested
    @DisplayName("update 部分更新")
    class Update {

        private ModelGroup base() {
            return ModelGroup.create("n", "basic", new BigDecimal("1.0"),
                    ModelNames.of(List.of("gpt-4o")), AccessPolicy.PUBLIC, "d", NOW);
        }

        @Test
        @DisplayName("非 null 字段才覆盖，code 不变，刷新 updatedTime")
        void partialUpdate() {
            ModelGroup g = base();
            g.update("新名", new BigDecimal("2.0"), ModelNames.of(List.of("claude-3-opus")),
                    AccessPolicy.PRIVATE, "新描述", NOW + 100);

            assertEquals("新名", g.name());
            assertEquals("basic", g.code()); // 不变
            assertEquals(new BigDecimal("2.0"), g.basePriceRatio().value());
            assertEquals(List.of("claude-3-opus"), g.models().values());
            assertEquals(AccessPolicy.PRIVATE, g.accessPolicy());
            assertEquals("新描述", g.description());
            assertEquals(NOW + 100, g.updatedTime());
        }

        @Test
        @DisplayName("全 null → 仅刷新 updatedTime，其余不变")
        void allNullKeepsState() {
            ModelGroup g = base();
            g.update(null, null, null, null, null, NOW + 50);
            assertEquals("n", g.name());
            assertEquals(new BigDecimal("1.0"), g.basePriceRatio().value());
            assertEquals(NOW + 50, g.updatedTime());
        }

        @Test
        @DisplayName("空白描述 → 清空为 null")
        void blankDescriptionClears() {
            ModelGroup g = base();
            g.update(null, null, null, null, "   ", NOW + 1);
            assertNull(g.description());
        }
    }

    @Nested
    @DisplayName("状态与判定")
    class StatusAndPredicates {

        @Test
        @DisplayName("applyStatus 切换并刷新 updatedTime")
        void applyStatus() {
            ModelGroup g = ModelGroup.create("n", "basic", null,
                    ModelNames.of(List.of("gpt-4o")), AccessPolicy.PUBLIC, null, NOW);
            g.applyStatus(ModelGroupStatus.DISABLED, NOW + 10);
            assertEquals(ModelGroupStatus.DISABLED, g.status());
            assertEquals(NOW + 10, g.updatedTime());
        }

        @Test
        @DisplayName("isSelectable：启用且模型集非空才可选用")
        void isSelectable() {
            ModelGroup withModels = ModelGroup.create("n", "a", null,
                    ModelNames.of(List.of("gpt-4o")), AccessPolicy.PUBLIC, null, NOW);
            assertTrue(withModels.isSelectable());

            ModelGroup empty = ModelGroup.create("n", "b", null, null, AccessPolicy.PUBLIC, null, NOW);
            assertFalse(empty.isSelectable()); // 空模型集不可选

            withModels.applyStatus(ModelGroupStatus.DISABLED, NOW);
            assertFalse(withModels.isSelectable()); // 禁用不可选
        }

        @Test
        @DisplayName("containsModel：判定模型归属")
        void containsModel() {
            ModelGroup g = ModelGroup.create("n", "a", null,
                    ModelNames.of(List.of("gpt-4o", "claude-3-opus")), AccessPolicy.PUBLIC, null, NOW);
            assertTrue(g.containsModel("gpt-4o"));
            assertTrue(g.containsModel(" claude-3-opus "));
            assertFalse(g.containsModel("gemini"));
            assertFalse(g.containsModel(null));
        }
    }

    @Nested
    @DisplayName("builder 持久化重建")
    class Rebuild {

        @Test
        @DisplayName("装配字段不触发创建校验")
        void rebuild() {
            ModelGroup g = ModelGroup.builder()
                    .id(7L).name("n").code("basic").basePriceRatio(new BigDecimal("1.2"))
                    .models(ModelNames.of(List.of("gpt-4o"))).accessPolicy(AccessPolicy.AUTO_LEVEL)
                    .status(ModelGroupStatus.ENABLED).description("d")
                    .createdTime(NOW).updatedTime(NOW).build();
            assertEquals(7L, g.id());
            assertEquals("basic", g.code());
            assertEquals(AccessPolicy.AUTO_LEVEL, g.accessPolicy());
        }

        @Test
        @DisplayName("倍率 null → 归一 1.0；models null → 空集")
        void rebuildNullDefaults() {
            ModelGroup g = ModelGroup.builder()
                    .id(1L).name("n").code("c").basePriceRatio(null).models(null)
                    .accessPolicy(AccessPolicy.PUBLIC).status(ModelGroupStatus.ENABLED)
                    .createdTime(NOW).updatedTime(NOW).build();
            assertEquals(BigDecimal.ONE, g.basePriceRatio().value());
            assertTrue(g.models().isEmpty());
        }
    }
}
