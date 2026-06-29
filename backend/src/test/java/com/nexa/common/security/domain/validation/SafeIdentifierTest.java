package com.nexa.common.security.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SafeIdentifier} 值对象单测（纯 JUnit，不起 Spring/DB）。
 *
 * <p>覆盖防 SQL 注入的「结构性标识符白名单 + 字符约束」双保险逻辑：合法标识符放行、白名单外拒绝、
 * 含注入字符（引号/分号/注释符/空白）拒绝、空值拒绝、值相等语义。按正常/边界/异常三类组织
 * （backend-engineer §3.3）。这是本切片「防 SQL 注入」覆盖参数化盲区那一层的核心逻辑，必测。</p>
 */
@DisplayName("SafeIdentifier 安全标识符值对象")
class SafeIdentifierTest {

    /** 模拟某查询 ORDER BY 列名的白名单。 */
    private static final Set<String> ALLOW = Set.of("created_at", "updated_at", "name", "id");

    @Nested
    @DisplayName("of 校验工厂")
    class Of {

        @Test
        @DisplayName("正常：白名单内且字符合法的标识符通过")
        void acceptsWhitelisted() {
            SafeIdentifier id = SafeIdentifier.of("created_at", ALLOW);
            assertEquals("created_at", id.value());
        }

        @Test
        @DisplayName("异常：白名单外的合法字符标识符被拒")
        void rejectsNotInAllowList() {
            // password 字符集合法，但不在该查询白名单 → 拒绝（防越权选列）。
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SafeIdentifier.of("password", ALLOW));
            assertTrue(ex.getMessage().contains("not allowed"));
        }

        @Test
        @DisplayName("异常：含单引号的注入 payload 被字符约束拦截")
        void rejectsQuoteInjection() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeIdentifier.of("name'; DROP TABLE users;--", ALLOW));
        }

        @Test
        @DisplayName("异常：含分号/空白/注释符一律被拒")
        void rejectsDangerousChars() {
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("a;b", ALLOW));
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("a b", ALLOW));
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("a--b", ALLOW));
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("a/*x*/b", ALLOW));
        }

        @Test
        @DisplayName("异常：字符合法但不在白名单的注入式列名仍被拒（字符约束兜底前白名单已拦）")
        void rejectsUnknownEvenIfCharsLegal() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeIdentifier.of("secret_column", ALLOW));
        }

        @Test
        @DisplayName("边界：空字符串拒绝")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("", ALLOW));
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("   ", ALLOW));
        }

        @Test
        @DisplayName("边界：null 标识符拒绝")
        void rejectsNullRaw() {
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of(null, ALLOW));
        }

        @Test
        @DisplayName("边界：null 白名单视为全部拒绝")
        void rejectsNullAllowList() {
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("name", null));
        }

        @Test
        @DisplayName("边界：空白名单等于全部拒绝")
        void rejectsEmptyAllowList() {
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of("name", Set.of()));
        }

        @Test
        @DisplayName("边界：超长（>64 字符）标识符被字符约束拒绝")
        void rejectsTooLong() {
            String tooLong = "a".repeat(65);
            assertThrows(IllegalArgumentException.class, () -> SafeIdentifier.of(tooLong, Set.of(tooLong)));
        }
    }

    @Nested
    @DisplayName("值语义（按值相等）")
    class ValueSemantics {

        @Test
        @DisplayName("相同值的两个实例相等且 hashCode 一致")
        void equalByValue() {
            SafeIdentifier a = SafeIdentifier.of("name", ALLOW);
            SafeIdentifier b = SafeIdentifier.of("name", ALLOW);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同值不相等")
        void notEqualByValue() {
            SafeIdentifier a = SafeIdentifier.of("name", ALLOW);
            SafeIdentifier b = SafeIdentifier.of("id", ALLOW);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("toString 返回标识符值（结构片段可读）")
        void toStringIsValue() {
            assertEquals("name", SafeIdentifier.of("name", ALLOW).toString());
        }
    }
}
