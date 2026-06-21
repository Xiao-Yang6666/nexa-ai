package com.nexa.deployment.domain.vo;

import com.nexa.deployment.domain.exception.InvalidDeploymentParameterException;

/**
 * 容器 ID 值对象（io.net container 标识，F-3055/F-3056）。
 *
 * <p>不可变、按值相等。守护「容器详情查询」「容器日志查询」的容器标识非空不变量。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.4：
 * <ul>
 *   <li>F-3055 容器详情：{@code container_id} 为空→「container_id is required」</li>
 *   <li>F-3056 容器日志：{@code container_id} 缺失→「container_id parameter is required」</li>
 * </ul>
 * 两端点空值文案不同（一为 path 段、一为 query 参数），故提供两个工厂方法分别承载契约文案，
 * 而非在调用方各写一段字符串。</p>
 *
 * @param value 容器 ID（保证非空白）
 */
public record ContainerId(String value) {

    /**
     * 紧凑构造器：构造即校验非空白（默认采用 path 段文案）。
     *
     * @throws InvalidDeploymentParameterException 当 value 为 null/空白
     */
    public ContainerId {
        if (value == null || value.isBlank()) {
            throw new InvalidDeploymentParameterException("container_id is required");
        }
        value = value.trim();
    }

    /**
     * 容器详情语义工厂（F-3055，path 段缺失文案「container_id is required」）。
     *
     * @param raw 原始容器 ID（可空）
     * @return 校验通过的容器 ID
     * @throws InvalidDeploymentParameterException 空白→required
     */
    public static ContainerId forDetail(String raw) {
        return new ContainerId(raw);
    }

    /**
     * 容器日志语义工厂（F-3056，query 参数缺失文案「container_id parameter is required」）。
     *
     * @param raw query 参数 {@code container_id} 原始值（可空）
     * @return 校验通过的容器 ID
     * @throws InvalidDeploymentParameterException 空白→parameter is required
     */
    public static ContainerId forLogs(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDeploymentParameterException("container_id parameter is required");
        }
        return new ContainerId(raw.trim());
    }
}
