package com.nexa.relay.domain.exception;

/**
 * 端点级减法约束拒绝异常（key 的 EndpointLimits 命中拒绝，403）。
 *
 * <p>领域规则来源：prd-model ML-8 §3（key 级减法约束，端点维）+ BILLING-MODEL-ARCHITECTURE §4.2
 * （端点校验在 TokenAuth 后、L1 前）+ DATA-MODEL Token 端点扩展（{@code endpoint_limits} JSON 入站
 * 协议集，如 {@code ["openai","claude"]}）。当 token 启用端点级减法约束（{@code EndpointLimitsEnabled=true}）
 * 且本次请求入站协议 inFmt 不在允许集时抛出。</p>
 *
 * <p>语义为<b>自我约束（减法）非权限闸门</b>：模型/端点本就全开（ML-8），key 持有者只能做减法收窄
 * 自身可用范围，无加法授权路径，天然安全。复用 {@link ModelMappingException} 的 key 减法拒绝语义同源
 * （403 拒绝态），但端点维单列稳定错误码便于客户端/可观测区分。</p>
 *
 * <p>安全：message 仅含入站协议线值（如 {@code "claude"}，非敏感），绝不含 token key / 上游凭证。</p>
 */
public class EndpointNotAllowedException extends DomainException {

    /**
     * @param inboundFormatWire 被拒的入站协议线值（如 {@code "openai"}/{@code "claude"}，非敏感）
     */
    public EndpointNotAllowedException(String inboundFormatWire) {
        super("ENDPOINT_NOT_ALLOWED", 403,
                "endpoint not allowed by key endpoint_limits: " + inboundFormatWire);
    }
}
