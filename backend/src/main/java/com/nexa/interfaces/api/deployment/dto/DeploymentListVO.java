package com.nexa.interfaces.api.deployment.dto;

import com.nexa.domain.deployment.model.DeploymentList;
import com.nexa.domain.deployment.model.DeploymentSummary;

import java.util.List;
import java.util.Map;

/**
 * 部署列表/搜索出参视图（管理端视图，F-3041/F-3042）。
 *
 * <p>对齐契约出参（API-ENDPOINTS §10.2）：{@code items[]} = 各部署概要 6 字段、
 * {@code status_counts = { all, running, ... }}（领域派生）、{@code total}/{@code page}/{@code page_size}。
 * 管理端视图：部署概要不含成本/利润/上游模型 B 等敏感字段（仅运维所需的状态/时长/硬件摘要）。</p>
 *
 * @param items        部署概要行
 * @param statusCounts 状态计数（含 all + 各状态）
 * @param total        总数（列表=上游 total；搜索关键词过滤后=过滤数量）
 * @param page         当前页号
 * @param pageSize     每页条数
 */
public record DeploymentListVO(
        List<Item> items,
        Map<String, Long> statusCounts,
        long total,
        int page,
        int pageSize) {

    /**
     * 部署概要行视图（契约 items[] 元素）。
     *
     * @param deploymentId            部署 ID
     * @param name                    部署名称
     * @param status                  部署状态
     * @param timeRemaining           剩余时间（可空）
     * @param hardwareInfo            硬件信息摘要（可空）
     * @param computeMinutesRemaining 剩余计算分钟（可空）
     */
    public record Item(
            String deploymentId,
            String name,
            String status,
            String timeRemaining,
            String hardwareInfo,
            Long computeMinutesRemaining) {

        /**
         * 从领域概要投影为行视图。
         *
         * @param s 部署概要
         * @return 行视图
         */
        static Item from(DeploymentSummary s) {
            return new Item(
                    s.deploymentId(),
                    s.name(),
                    s.status(),
                    s.timeRemaining(),
                    s.hardwareInfo(),
                    s.computeMinutesRemaining());
        }
    }

    /**
     * 从领域聚合投影为出参视图（status_counts 取自聚合派生）。
     *
     * @param list 部署列表聚合
     * @return 出参视图
     */
    public static DeploymentListVO from(DeploymentList list) {
        List<Item> items = list.items().stream().map(Item::from).toList();
        return new DeploymentListVO(
                items,
                list.statusCounts(),
                list.total(),
                list.page(),
                list.pageSize());
    }
}
