package com.nexa.domain.deployment.vo;

import com.nexa.domain.deployment.exception.InvalidDeploymentParameterException;

/**
 * 集群名称值对象（部署集群命名，F-3053）。
 *
 * <p>不可变、按值相等。守护「集群名称可用性查询」的非空不变量。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.3 F-3053「name 为空→name parameter is required」。</p>
 *
 * @param value 集群名称（保证非空白）
 */
public record ClusterName(String value) {

    /**
     * 紧凑构造器：构造即校验非空白。
     *
     * @throws InvalidDeploymentParameterException 当 value 为 null/空白
     */
    public ClusterName {
        if (value == null || value.isBlank()) {
            throw new InvalidDeploymentParameterException("name parameter is required");
        }
        value = value.trim();
    }
}
