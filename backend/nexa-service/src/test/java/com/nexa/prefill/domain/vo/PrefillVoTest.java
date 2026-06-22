package com.nexa.prefill.domain.vo;

import com.nexa.prefill.domain.exception.InvalidPrefillParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrefillType} 与 {@link PrefillItems} 值对象单测（纯 JUnit）。
 *
 * <p>覆盖 type 枚举解析（合法/大小写/空白/非法 → 400 信号）与 items 规范化（去空白/去重/保序/
 * null 安全），对齐 DB-SCHEMA §17 与 PRD 模块十五 §14。</p>
 */
@DisplayName("PrefillType / PrefillItems 值对象")
class PrefillVoTest {

    @DisplayName("fromWire 解析合法枚举（大小写/空白不敏感）")
    @Test
    void fromWireValid() {
        assertEquals(PrefillType.MODEL, PrefillType.fromWire("model"));
        assertEquals(PrefillType.TAG, PrefillType.fromWire("  TAG "));
        assertEquals(PrefillType.ENDPOINT, PrefillType.fromWire("Endpoint"));
        assertEquals("model", PrefillType.MODEL.wireValue());
    }

    @DisplayName("fromWire 非法枚举 → 抛 InvalidPrefillParameter（400 信号）")
    @Test
    void fromWireInvalid() {
        assertThrows(InvalidPrefillParameterException.class, () -> PrefillType.fromWire("group"));
        assertThrows(InvalidPrefillParameterException.class, () -> PrefillType.fromWire(""));
        assertThrows(InvalidPrefillParameterException.class, () -> PrefillType.fromWire(null));
    }

    @DisplayName("items 规范化：去 null/空白条目、去首尾空白、保序去重")
    @Test
    void itemsNormalization() {
        PrefillItems items = PrefillItems.of(Arrays.asList("gpt-4o", " gpt-4o ", "", "  ", null, "claude"));
        // "gpt-4o" 出现两次（含带空白版本）→ 去重保首次；空/空白/null 剔除；保序
        assertEquals(List.of("gpt-4o", "claude"), items.values());
        assertEquals(2, items.size());
    }

    @DisplayName("items 为 null/空 → EMPTY 单例")
    @Test
    void itemsEmpty() {
        assertSame(PrefillItems.EMPTY, PrefillItems.of(null));
        assertSame(PrefillItems.EMPTY, PrefillItems.of(List.of()));
        assertTrue(PrefillItems.EMPTY.isEmpty());
    }

    @DisplayName("items 值相等（值对象按值相等）")
    @Test
    void itemsValueEquality() {
        assertEquals(PrefillItems.of(List.of("a", "b")), PrefillItems.of(List.of("a", "b")));
        assertEquals(PrefillItems.of(List.of("a", "b")).hashCode(),
                PrefillItems.of(List.of("a", "b")).hashCode());
    }
}
