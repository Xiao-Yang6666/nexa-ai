package com.nexa.compliance.domain.service;

import com.nexa.compliance.domain.exception.CrossBorderRoutingDeniedException;
import com.nexa.compliance.domain.vo.DataResidency;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 合规分组选渠领域服务（F-5018 合规分组仅境内 provider，DC-008）。
 *
 * <p>纯领域规则（零框架依赖，可纯 JUnit 单测）：判定某「分组」是否为合规分组、并据渠道
 * {@link DataResidency} 过滤出合规可用的候选渠道。领域规则来源：API-ENDPOINTS §14.5 F-5018
 * 「合规分组限定仅命中境内数据驻地渠道」、Compliance 验收「合规分组请求不命中境外渠道」。</p>
 *
 * <p>设计：选渠层（{@code com.nexa.routing}）拿到「请求归属分组 + 候选渠道及其驻地」后调用本服务，
 * 把「哪些分组算合规、合规分组如何剔除境外渠道」的横切规则收敛于此，避免散落到选渠主流程
 * （领域服务，backend-engineer §2.4 跨聚合规则）。本服务不持有状态，方法无副作用。</p>
 */
public final class ComplianceGroupPolicy {

    /**
     * 合规分组名集合（约定）。
     *
     * <p>命中其一即视为「要求数据不出境」的合规分组。取约定名 {@code compliance}（含其大小写变体）
     * + {@code domestic-only}；具体分组名由运营在分组管理中创建并套用 group_ratio，本服务只认这些
     * 语义化前缀/全名。后续若分组语义扩展，集中在此调整。</p>
     */
    private static final Set<String> COMPLIANCE_GROUP_NAMES = Set.of("compliance", "domestic-only");

    private ComplianceGroupPolicy() {
    }

    /**
     * 判断给定分组是否为「合规分组」（要求数据不出境）。
     *
     * @param group 请求归属分组标识（可为 null/空白 → 非合规分组）
     * @return 合规分组返回 {@code true}
     */
    public static boolean isComplianceGroup(String group) {
        if (group == null || group.isBlank()) {
            return false;
        }
        return COMPLIANCE_GROUP_NAMES.contains(group.trim().toLowerCase());
    }

    /**
     * 判定某渠道（按其数据驻地）是否允许被「该分组」命中。
     *
     * <p>领域规则：合规分组仅允许境内驻地渠道（{@link DataResidency#isDomestic()}）；
     * 非合规分组不限制驻地（境内外均可）。</p>
     *
     * @param group           请求归属分组
     * @param channelResidency 候选渠道的数据驻地
     * @return 允许命中返回 {@code true}
     * @throws NullPointerException channelResidency 为空
     */
    public static boolean isChannelAllowedForGroup(String group, DataResidency channelResidency) {
        Objects.requireNonNull(channelResidency, "channelResidency");
        if (!isComplianceGroup(group)) {
            return true; // 非合规分组：不限制驻地。
        }
        return channelResidency.isDomestic(); // 合规分组：仅境内。
    }

    /**
     * 从候选渠道中过滤出「该分组合规可用」的子集（保持原顺序，选渠层再据权重挑选）。
     *
     * <p>领域规则：合规分组剔除所有境外候选；非合规分组原样返回。这是「合规分组请求不命中境外渠道」
     * 验收的领域落地——选渠层先用本方法收窄候选集，再走原有权重/亲和选择，杜绝境外渠道进入候选。</p>
     *
     * @param group      请求归属分组
     * @param candidates 候选渠道（携带各自驻地）
     * @param <T>        渠道候选载体类型（由选渠层提供，含 residency 提取器）
     * @param residencyOf 从候选提取其数据驻地的函数
     * @return 合规可用的候选子集
     */
    public static <T> List<T> filterAllowed(String group,
                                            List<T> candidates,
                                            java.util.function.Function<T, DataResidency> residencyOf) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(residencyOf, "residencyOf");
        if (!isComplianceGroup(group)) {
            return List.copyOf(candidates); // 非合规分组：不过滤。
        }
        return candidates.stream()
                .filter(c -> residencyOf.apply(c).isDomestic())
                .toList();
    }

    /**
     * 断言「合规分组选渠结果」未命中境外渠道（命令版护栏，选渠收尾自检）。
     *
     * <p>领域规则：合规分组最终选中的渠道驻地必须为境内，否则抛
     * {@link CrossBorderRoutingDeniedException}（→ 选渠层拒绝该请求 / 重选）。用于在过滤后做防御式
     * 二次校验，杜绝因配置遗漏导致合规分组请求被路由到境外（F-5018 强约束）。</p>
     *
     * @param group           请求归属分组
     * @param selectedResidency 最终选中渠道的数据驻地
     * @throws CrossBorderRoutingDeniedException 合规分组却选中境外渠道
     */
    public static void assertNotCrossBorder(String group, DataResidency selectedResidency) {
        Objects.requireNonNull(selectedResidency, "selectedResidency");
        if (isComplianceGroup(group) && selectedResidency.crossesBorder()) {
            // 不回显具体渠道/区域细节给客户，仅给稳定合规拒绝语义（避免泄露供应商拓扑）。
            throw new CrossBorderRoutingDeniedException(
                    "合规分组请求不可路由至境外渠道");
        }
    }
}
