package com.nexa.deployment.domain.vo;

import com.nexa.deployment.domain.exception.InvalidDeploymentParameterException;

/**
 * 部署 ID 值对象（io.net deployment 标识）。
 *
 * <p>不可变、按值相等。守护「按 id 定位部署」类端点（详情/容器列表/容器详情/容器日志，
 * F-3043/F-3054/F-3055/F-3056）的非空不变量。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.2 F-3043「id 为空→deployment ID is required」、
 * §10.4 容器端点共用同一前置（path id 必填）。文案在领域层固化，多端点复用。</p>
 *
 * @param value 部署 ID（保证非空白）
 */
public record DeploymentId(String value) {

    /**
     * 紧凑构造器：构造即校验非空白。
     *
     * @throws InvalidDeploymentParameterException 当 value 为 null/空白
     */
    public DeploymentId {
        if (value == null || value.isBlank()) {
            throw new InvalidDeploymentParameterException("deployment ID is required");
        }
        value = value.trim();
    }
}
