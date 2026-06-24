package com.nexa.relay.domain.service;

import com.nexa.relay.domain.exception.ModelMappingException;
import com.nexa.relay.domain.vo.ModelResolution;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 两层模型映射解析领域服务（RL-7 第②步 C→A→B，ADR-COMPAT-03，纯函数零框架依赖）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §3.1/§3.2 + prd-relay RL-7。执行次序铁律：
 * <pre>
 *   第1步【客户层 L1】 C → A   (UserModelAlias，user>group 优先级，1对1 纯字符串替换)
 *   第2步【超管层 L2】 A → B   (已下沉渠道级：选渠后由 Channel.modelMapping 解析，此处 l2Lookup 传 null 恒等)
 *   最终调用 B
 * </pre>
 * <ul>
 *   <li>先客户层再超管层；任一层未命中则该层恒等（C 未配则 A=C；A 未配底仓则 B=A）；</li>
 *   <li>1对1 纯字符串替换 + 环检测 + 最大跳数（FL-model 内核，防 A→B→A 死循环）；</li>
 *   <li>映射不掺路由（ADR-COMPAT-03）：只产出模型名，负载/容灾由 Ability 完成。</li>
 * </ul>
 * </p>
 *
 * <p>映射查找以函数式抽象（{@code Function<String,String>}）注入，domain 不依赖仓储实现：
 * <ul>
 *   <li>{@code l1Lookup}：C → A（命中返回 A，未命中返回 null）；</li>
 *   <li>{@code l2Lookup}：A → B（命中返回 B，未命中返回 null）。</li>
 * </ul>
 * </p>
 */
public final class TwoLayerModelResolver {

    /** 链式映射最大跳数（防恶意/误配的超长链，FL-model 内核约束）。 */
    public static final int MAX_HOPS = 16;

    private TwoLayerModelResolver() {
    }

    /**
     * 解析两层映射（C→A→B）。
     *
     * <p>每层支持链式叠加（A1→A2→...直到不再命中或成环），各层独立做环检测。</p>
     *
     * @param requested 客户输入名 C
     * @param l1Lookup  客户层查找（C→A），未命中返回 null
     * @param l2Lookup  超管层查找（A→B），未命中返回 null
     * @return 三段映射结果
     * @throws ModelMappingException 映射成环或超最大跳数
     */
    public static ModelResolution resolve(String requested,
                                          Function<String, String> l1Lookup,
                                          Function<String, String> l2Lookup) {
        if (requested == null || requested.isBlank()) {
            throw new ModelMappingException("EMPTY_MODEL", 400, "requested model must not be blank");
        }
        // L1 客户层链式 C→A（恒等：未命中则 A=C）
        String resolvedPublic = chainResolve(requested, l1Lookup);
        boolean l1Applied = !resolvedPublic.equals(requested);

        // L2 超管层链式 A→B（恒等：未命中则 B=A）
        String upstream = chainResolve(resolvedPublic, l2Lookup);
        boolean l2Applied = !upstream.equals(resolvedPublic);

        return new ModelResolution(requested, resolvedPublic, upstream, l1Applied, l2Applied);
    }

    /**
     * 链式解析单层（带环检测 + 最大跳数）。
     *
     * @param start  起始名
     * @param lookup 查找函数（命中返回下一跳，未命中返回 null/相同值停止）
     * @return 链尾名（不再命中时的值）
     * @throws ModelMappingException 成环或超跳数
     */
    private static String chainResolve(String start, Function<String, String> lookup) {
        String current = start;
        Set<String> visited = new LinkedHashSet<>();
        visited.add(current);
        int hops = 0;
        while (true) {
            String next = lookup.apply(current);
            // 未命中 / 自映射 / 相同值 → 停止（恒等）
            if (next == null || next.isBlank() || next.equals(current)) {
                return current;
            }
            if (++hops > MAX_HOPS) {
                throw ModelMappingException.cycleDetected(start);
            }
            if (!visited.add(next)) {
                // 已访问过 → 成环
                throw ModelMappingException.cycleDetected(start);
            }
            current = next;
        }
    }
}
