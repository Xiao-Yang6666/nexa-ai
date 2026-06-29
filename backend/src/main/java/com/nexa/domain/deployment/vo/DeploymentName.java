package com.nexa.domain.deployment.vo;

import com.nexa.domain.deployment.exception.InvalidDeploymentParameterException;

/**
 * 部署名称值对象（部署重命名入参，F-3046）。
 *
 * <p>不可变、按值相等。守护「部署重命名」端点（API-ENDPOINTS §10.2 PUT /api/deployments/{id}/name）
 * 的非空不变量。与 {@link ClusterName}（名称可用性查询入参）拆分为两个值对象：虽都表达「集群/部署名」，
 * 但契约错误文案不同（rename 空→「deployment name cannot be empty」，check-name 空→
 * 「name parameter is required」），各自固化文案避免混淆。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.2 F-3046「name 为空→deployment name cannot be empty」。</p>
 *
 * @param value 部署名称（保证非空白）
 */
public record DeploymentName(String value) {

    /**
     * 紧凑构造器：构造即校验非空白。
     *
     * @throws InvalidDeploymentParameterException 当 value 为 null/空白
     */
    public DeploymentName {
        if (value == null || value.isBlank()) {
            throw new InvalidDeploymentParameterException("deployment name cannot be empty");
        }
        value = value.trim();
    }
}
