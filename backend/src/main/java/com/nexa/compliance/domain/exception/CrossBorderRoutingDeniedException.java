package com.nexa.compliance.domain.exception;

/**
 * 合规分组命中境外渠道被拒异常（F-5018，DC-008）。
 *
 * <p>当请求归属「合规分组」（要求数据不出境），而选渠层挑出的渠道数据驻地为境外时抛出。
 * 领域规则来源：API-ENDPOINTS §14.5 F-5018「合规分组限定仅命中境内数据驻地渠道」、
 * Compliance 验收「合规分组请求不命中境外渠道」。接口层/选渠层据此剔除境外候选并在无境内候选时拒绝。</p>
 */
public final class CrossBorderRoutingDeniedException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "CROSS_BORDER_ROUTING_DENIED";

    /**
     * 构造合规分组境外渠道被拒异常。
     *
     * @param message 稳定提示（不回显渠道/上游细节，避免泄露供应商拓扑）
     */
    public CrossBorderRoutingDeniedException(String message) {
        super(CODE, message);
    }
}
