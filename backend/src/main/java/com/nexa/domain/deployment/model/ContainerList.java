package com.nexa.domain.deployment.model;

import java.util.List;

/**
 * 部署容器列表结果聚合（充血领域模型，F-3054）。
 *
 * <p>承载「部署容器列表」整体结果并守护聚合不变量：契约出参 {@code { containers[], total }}
 * （API-ENDPOINTS §10.4）。{@code total} 由聚合从容器列表派生（无容器→空数组 + total=0），
 * 保证总数与列表一致（充血模型，避免外部传入不一致的 total）。</p>
 *
 * @param containers 容器列表（只读，可空→空列表）
 */
public record ContainerList(List<ContainerSummary> containers) {

    /**
     * 紧凑构造器：防御式拷贝列表为不可变列表（无容器归一空列表）。
     */
    public ContainerList {
        containers = containers == null ? List.of() : List.copyOf(containers);
    }

    /**
     * 容器总数（契约 {@code total}=容器数；无容器→0）。
     *
     * @return 容器数量
     */
    public int total() {
        return containers.size();
    }
}
