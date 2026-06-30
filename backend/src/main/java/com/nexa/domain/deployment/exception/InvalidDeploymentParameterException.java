package com.nexa.domain.deployment.exception;

import com.nexa.domain.kernel.DomainException;

/**
 * 部署管理参数非法异常（接口层映射 400）。
 *
 * <p>当请求缺失必填参数或参数取值越界时由领域值对象抛出，承载契约（API-ENDPOINTS §10.3/§10.4）
 * 约定的稳定面向用户的错误文案。例如：</p>
 * <ul>
 *   <li>{@code hardware_id parameter is required}（F-3051 缺失 hardware_id）</li>
 *   <li>{@code invalid hardware_id parameter}（F-3051 hardware_id 非法/&lt;=0）</li>
 *   <li>{@code deployment ID is required}（F-3043 缺 id）</li>
 *   <li>{@code container_id parameter is required}（F-3056 缺 container_id）</li>
 *   <li>{@code name parameter is required}（F-3053 缺 name）</li>
 * </ul>
 *
 * <p>领域规则来源：API-ENDPOINTS 模块十各端点「错误码」段。错误文案在领域层固化，避免散落到
 * 接口层（保证多端点共用同一文案）。</p>
 */
public class InvalidDeploymentParameterException extends DomainException {

    /**
     * @param message 面向用户的参数错误文案（契约固定文案，见类注释）
     */
    public InvalidDeploymentParameterException(String message) {
        super("INVALID_DEPLOYMENT_PARAMETER", message);
    }
}
