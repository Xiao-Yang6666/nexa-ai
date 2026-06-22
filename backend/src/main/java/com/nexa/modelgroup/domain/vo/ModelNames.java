package com.nexa.modelgroup.domain.vo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 模型组可用模型集合值对象。
 *
 * <p>不可变、按值相等、零框架依赖（DDD 值对象，backend-engineer §2.4）。承载一个模型组对外开放的
 * 模型名列表（如 {@code ["gpt-4o","claude-3-opus","gemini-pro"]}）——管理员把若干供应商模型纳入同一
 * 模型组售卖，中继链路据此判定某模型是否属于客户选中的模型组。</p>
 *
 * <p>规范化不变量：构造期去除 null / 空白条目、去首尾空白、保序去重，避免脏模型名落库。空列表合法
 * （模型组可先建后填），但中继选组时空模型集等价「无可用模型」。模型名与 JSON 串的互转由基础设施层
 * 负责，本 VO 只持有规范化后的 {@code List<String>}。</p>
 */
public final class ModelNames {

    /** 空模型集常量（创建时未指定 models）。 */
    public static final ModelNames EMPTY = new ModelNames(List.of());

    private final List<String> values;

    private ModelNames(List<String> values) {
        // 防御性不可变拷贝：外部传入列表后续被改不影响本 VO（值对象不可变）。
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * 工厂方法：规范化并构造模型集合。
     *
     * <p>规范化规则：剔除 null / 纯空白条目、对每个条目去首尾空白、保持原始顺序并去重（首次出现保留）。
     * {@code null} 入参等价空集合。</p>
     *
     * @param raw 原始模型名列表（可空、可含空白/重复）
     * @return 规范化后的模型集合值对象
     */
    public static ModelNames of(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        List<String> normalized = new ArrayList<>(raw.size());
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isEmpty() && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return new ModelNames(normalized);
    }

    /**
     * 是否包含指定模型名（中继链路判定客户请求模型是否落在选中模型组内）。
     *
     * @param modelName 待判定模型名（null/空白 → false）
     * @return 包含返回 {@code true}
     */
    public boolean contains(String modelName) {
        if (modelName == null) {
            return false;
        }
        return values.contains(modelName.trim());
    }

    /** @return 不可变模型名列表（规范化后） */
    public List<String> values() {
        return values;
    }

    /** @return 模型数量 */
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
        if (!(o instanceof ModelNames other)) {
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
        return "ModelNames" + values;
    }
}
