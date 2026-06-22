package com.nexa.prefill.domain.model;

import com.nexa.prefill.domain.exception.InvalidPrefillParameterException;
import com.nexa.prefill.domain.vo.PrefillItems;
import com.nexa.prefill.domain.vo.PrefillType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrefillGroup} 预填分组聚合根单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖 PRD 模块十五 §14 领域规则：创建校验（name/type 必填 + 长度）、充血行为
 * （rename/replaceItems 的 null 守卫与校验、updatedTime 刷新）、type 不可变——按正常/边界/异常
 * 三类组织（backend-engineer §3.3）。名称冲突属应用层（需查库），不在聚合单测范围。</p>
 */
@DisplayName("PrefillGroup 预填分组聚合根")
class PrefillGroupTest {

    private static final long NOW = 1_700_000_000L;

    @Nested
    @DisplayName("create 创建工厂")
    class Create {

        @Test
        @DisplayName("合法入参 → 规范化条目、时间戳、id 为 null")
        void validCreate() {
            PrefillGroup g = PrefillGroup.create(
                    "  gpt-models ", PrefillType.MODEL,
                    PrefillItems.of(List.of("gpt-4o", "gpt-4o", " gpt-3.5 ")), "desc", NOW);

            assertNull(g.id());
            assertEquals("gpt-models", g.name()); // 去首尾空白
            assertEquals(PrefillType.MODEL, g.type());
            // 条目去重 + 去空白：["gpt-4o","gpt-3.5"]
            assertEquals(List.of("gpt-4o", "gpt-3.5"), g.items().values());
            assertEquals("desc", g.description());
            assertEquals(NOW, g.createdTime());
            assertEquals(NOW, g.updatedTime());
        }

        @Test
        @DisplayName("items 为 null → 空集合")
        void nullItemsBecomesEmpty() {
            PrefillGroup g = PrefillGroup.create("n", PrefillType.TAG, null, null, NOW);
            assertTrue(g.items().isEmpty());
            assertNull(g.description());
        }

        @Test
        @DisplayName("name 为空 → 抛 InvalidPrefillParameter")
        void blankNameRejected() {
            assertThrows(InvalidPrefillParameterException.class,
                    () -> PrefillGroup.create("  ", PrefillType.MODEL, null, null, NOW));
        }

        @Test
        @DisplayName("name 超 64 长度 → 抛 InvalidPrefillParameter（边界）")
        void tooLongNameRejected() {
            String longName = "a".repeat(PrefillGroup.NAME_MAX_LENGTH + 1);
            assertThrows(InvalidPrefillParameterException.class,
                    () -> PrefillGroup.create(longName, PrefillType.MODEL, null, null, NOW));
        }

        @Test
        @DisplayName("name 恰好 64 长度 → 合法（边界）")
        void exactlyMaxNameAccepted() {
            String maxName = "a".repeat(PrefillGroup.NAME_MAX_LENGTH);
            PrefillGroup g = PrefillGroup.create(maxName, PrefillType.MODEL, null, null, NOW);
            assertEquals(maxName, g.name());
        }

        @Test
        @DisplayName("type 为 null → 抛 InvalidPrefillParameter")
        void nullTypeRejected() {
            assertThrows(InvalidPrefillParameterException.class,
                    () -> PrefillGroup.create("n", null, null, null, NOW));
        }
    }

    @Nested
    @DisplayName("rename 重命名行为")
    class Rename {

        @Test
        @DisplayName("新名合法 → 改名并刷新 updatedTime")
        void renameUpdatesNameAndTime() {
            PrefillGroup g = PrefillGroup.create("old", PrefillType.MODEL, null, null, NOW);
            g.rename("new-name", NOW + 100);
            assertEquals("new-name", g.name());
            assertEquals(NOW + 100, g.updatedTime());
        }

        @Test
        @DisplayName("newName 为 null → 不改名、updatedTime 不变（部分更新）")
        void renameWithNullIsNoop() {
            PrefillGroup g = PrefillGroup.create("keep", PrefillType.MODEL, null, null, NOW);
            g.rename(null, NOW + 100);
            assertEquals("keep", g.name());
            assertEquals(NOW, g.updatedTime());
        }

        @Test
        @DisplayName("新名为空白 → 抛 InvalidPrefillParameter")
        void renameBlankRejected() {
            PrefillGroup g = PrefillGroup.create("x", PrefillType.MODEL, null, null, NOW);
            assertThrows(InvalidPrefillParameterException.class, () -> g.rename("   ", NOW));
        }
    }

    @Nested
    @DisplayName("replaceItems 替换条目行为")
    class ReplaceItems {

        @Test
        @DisplayName("新条目 → 整体替换并刷新 updatedTime")
        void replaceUpdatesItems() {
            PrefillGroup g = PrefillGroup.create("x", PrefillType.MODEL,
                    PrefillItems.of(List.of("a")), null, NOW);
            g.replaceItems(PrefillItems.of(List.of("b", "c")), NOW + 50);
            assertEquals(List.of("b", "c"), g.items().values());
            assertEquals(NOW + 50, g.updatedTime());
        }

        @Test
        @DisplayName("null 条目 → 不改（部分更新），updatedTime 不变")
        void replaceWithNullIsNoop() {
            PrefillGroup g = PrefillGroup.create("x", PrefillType.MODEL,
                    PrefillItems.of(List.of("a")), null, NOW);
            g.replaceItems(null, NOW + 50);
            assertEquals(List.of("a"), g.items().values());
            assertEquals(NOW, g.updatedTime());
        }
    }
}
