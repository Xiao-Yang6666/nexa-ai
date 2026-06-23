package com.nexa.deployment.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 部署名称不可用异常（接口层映射 409 Conflict，F-3046）。
 *
 * <p>当部署重命名预检发现目标名称已被占用/不可用时由领域行为抛出，承载契约（API-ENDPOINTS §10.2 F-3046）
 * 约定的稳定文案「name is not available」。</p>
 *
 * <p>与 {@link InvalidDeploymentParameterException}（入参非法→400）、{@link IonetIntegrationException}
 * （上游故障→502）区分：本异常表达「请求合法、上游正常，但因名称冲突无法完成」的业务语义，对应 409。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.2 F-3046「名称不可用→name is not available」。</p>
 */
public class DeploymentNameUnavailableException extends DomainException {

    /** 契约固定文案（名称不可用）。 */
    public static final String MESSAGE = "name is not available";

    /**
     * 构造名称不可用异常（文案契约固定）。
     */
    public DeploymentNameUnavailableException() {
        super("DEPLOYMENT_NAME_UNAVAILABLE", MESSAGE);
    }
}
