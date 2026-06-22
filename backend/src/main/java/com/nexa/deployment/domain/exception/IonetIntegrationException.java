package com.nexa.deployment.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * io.net 上游集成异常（接口层映射 502 Bad Gateway）。
 *
 * <p>当 io.net 企业 API 未配置（缺 api_key）、连接失败、返回非法响应或上游 APIError 时抛出。
 * 与 {@link InvalidDeploymentParameterException}（客户端 4xx）区分：本异常表示上游/集成侧故障
 * （5xx 语义），message 透传上游 {@code APIError.Message}（空时回退稳定文案）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.1「key 无效→ionet.APIError.Message（空时回退 failed to validate
 * api key）」、§10「铁律：实际计费以 io.net 为准」。错误必须 wrap 带上游上下文不吞错
 * （backend-engineer §3.2）。</p>
 */
public class IonetIntegrationException extends DomainException {

    /**
     * @param message 上游错误描述（透传 io.net APIError.Message 或集成失败原因）
     */
    public IonetIntegrationException(String message) {
        super("IONET_INTEGRATION_ERROR", message);
    }
}
