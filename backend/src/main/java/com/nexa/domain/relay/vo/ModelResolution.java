package com.nexa.domain.relay.vo;

import java.util.Objects;

/**
 * 两层映射结果值对象（C→A→B 三段，RL-7 第②步产出）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §3 + prd-relay RL-7。承载映射后的三段模型名 + 各层是否命中，
 * 供后续选渠（用 B 查 Ability）、双价记账（售价用 A、成本用 B）、落 Log（C/A/B 三段落点）使用。</p>
 *
 * <p>恒等透传态（层未命中）：L1 未命中则 A=C；L2 未命中底仓则 B=A（ADR-COMPAT-03，FL-model 内核）。</p>
 *
 * @param requested      C 客户输入名（客户可见）
 * @param resolvedPublic A 平台公开名（L1 后，客户可见、定价键）
 * @param upstream       B 真实上游名（L2 后，客户不可见）
 * @param l1Applied      L1 客户层是否实际映射（C≠A）
 * @param l2Applied      L2 超管层是否实际映射（A≠B）
 */
public record ModelResolution(
        String requested,
        String resolvedPublic,
        String upstream,
        boolean l1Applied,
        boolean l2Applied
) {

    public ModelResolution {
        Objects.requireNonNull(requested, "requested(C) must not be null");
        Objects.requireNonNull(resolvedPublic, "resolvedPublic(A) must not be null");
        Objects.requireNonNull(upstream, "upstream(B) must not be null");
    }
}
