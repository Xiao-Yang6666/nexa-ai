package com.nexa.deployment.domain.model;

import com.nexa.deployment.domain.vo.Pagination;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署列表结果聚合（充血领域模型，F-3041/F-3042）。
 *
 * <p>承载「部署列表查询」（F-3041）与「部署搜索」（F-3042）的整体结果并守护派生不变量：</p>
 * <ul>
 *   <li><b>状态计数</b>（{@link #statusCounts()}）：契约 {@code status_counts = { all, running, completed,
 *       failed, ... }}（API-ENDPOINTS §10.2）。由本聚合<b>从 items 的 status 派生</b>，{@code all}=总条数、
 *       其余按各 status 出现次数汇总——避免计数与列表不一致（backend-engineer §2.2 充血模型）。</li>
 *   <li><b>本地名称过滤</b>（{@link #filterByKeyword(String)}）：F-3042「keyword 非空按名称小写包含过滤；
 *       total 按过滤后数量修正」。过滤是领域行为，产出一个 total 已修正的新聚合（不可变）。</li>
 * </ul>
 *
 * <p>{@code page}/{@code pageSize} 透传分页上下文（契约出参含 page/page_size）；{@code total} 为列表
 * 总数（列表查询取上游 total，搜索过滤后取过滤数量，由工厂选择）。状态过滤（F-3042 的 status 参数）是
 * 上游过滤（透传上游），不在本地做——本地只做关键词过滤，与契约一致。</p>
 *
 * @param items    部署概要列表（只读）
 * @param total    列表总数（列表查询=上游 total；搜索关键词过滤后=过滤数量）
 * @param page     当前页号
 * @param pageSize 每页条数
 */
public record DeploymentList(List<DeploymentSummary> items, long total, int page, int pageSize) {

    /**
     * 紧凑构造器：防御式拷贝列表为不可变列表。
     */
    public DeploymentList {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 列表查询工厂（F-3041）：total 取上游声明值。
     *
     * @param items        部署概要列表
     * @param upstreamTotal 上游声明的总数
     * @param pagination   归一后的分页参数
     * @return 部署列表聚合
     */
    public static DeploymentList ofListing(List<DeploymentSummary> items, long upstreamTotal, Pagination pagination) {
        return new DeploymentList(items, upstreamTotal, pagination.page(), pagination.pageSize());
    }

    /**
     * 状态计数（契约 {@code status_counts}，从 items 派生）。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.2 F-3041「status_counts = { all, running, completed, failed, ... }」。
     * {@code all} 恒为 items 总数；其余键按各部署 status 出现次数动态汇总（status 为空/null 归一为
     * {@code unknown} 桶，避免丢计数）。用 {@link LinkedHashMap} 保 {@code all} 在首位、其余按首次出现顺序。</p>
     *
     * @return 状态计数映射（含 all + 各状态计数）
     */
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", (long) items.size());
        for (DeploymentSummary s : items) {
            String key = (s.status() == null || s.status().isBlank()) ? "unknown" : s.status();
            counts.merge(key, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 按名称关键词本地过滤，产出 total 已修正的新聚合（F-3042）。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.2 F-3042「keyword 为空返回全部；非空按名称小写包含过滤；
     * total 按过滤后数量修正」。keyword 为 null/空白时原样返回（不过滤）；非空时小写化后逐项匹配，
     * 过滤后 total 修正为过滤数量、分页上下文保留。</p>
     *
     * @param keyword 名称关键词（可空，空则不过滤）
     * @return 过滤后的部署列表聚合（不可变）
     */
    public DeploymentList filterByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return this;
        }
        String needle = keyword.toLowerCase().trim();
        List<DeploymentSummary> filtered = items.stream()
                .filter(s -> s.nameContains(needle))
                .toList();
        // total 按过滤后数量修正（契约要求），page/pageSize 保留。
        return new DeploymentList(filtered, filtered.size(), page, pageSize);
    }
}
