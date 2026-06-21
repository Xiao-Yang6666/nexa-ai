package com.nexa.prefill.domain.vo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 预填分组条目集合值对象（DB-SCHEMA §17 PrefillGroup.items，JSON 字符串数组）。
 *
 * <p>不可变、按值相等、零框架依赖（DDD 值对象，backend-engineer §2.4）。承载一个预填分组下的
 * 条目列表（模型名 / 标签 / 端点名，依 {@link PrefillType} 而定），如
 * {@code ["gpt-4o","gpt-3.5-turbo"]}（DB-SCHEMA §17 示例）。</p>
 *
 * <p>规范化不变量：构造期去除 null / 空白条目、去首尾空白、保序去重——既避免脏条目落库，又让
 * 下拉填充（F-2014）拿到的列表干净无重复。空列表合法（分组可先建后填）。条目与 JSON 串的
 * 互转（序列化/反序列化）由基础设施层负责，本 VO 只持有规范化后的 {@code List<String>}。</p>
 */
public final class PrefillItems {

    /** 空条目集合常量（创建时未指定 items）。 */
    public static final PrefillItems EMPTY = new PrefillItems(List.of());

    private final List<String> values;

    private PrefillItems(List<String> values) {
        // 防御性不可变拷贝：外部传入列表后续被改不影响本 VO（值对象不可变）。
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * 工厂方法：规范化并构造条目集合。
     *
     * <p>规范化规则：剔除 null / 纯空白条目、对每个条目去首尾空白、保持原始顺序并去重
     * （首次出现保留）。{@code null} 入参等价空集合。</p>
     *
     * @param raw 原始条目列表（可空、可含空白/重复）
     * @return 规范化后的条目集合值对象
     */
    public static PrefillItems of(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        List<String> normalized = new ArrayList<>(raw.size());
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            // 跳过空白条目与重复条目（保序去重：下拉列表不应出现重复项）。
            if (!trimmed.isEmpty() && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return new PrefillItems(normalized);
    }

    /** @return 不可变条目列表（规范化后） */
    public List<String> values() {
        return values;
    }

    /** @return 条目数量 */
    public int size() {
        return values.size();
    }

    /** @return 是否为空集合 */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrefillItems other)) {
            return false;
        }
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "PrefillItems" + values;
    }
}
