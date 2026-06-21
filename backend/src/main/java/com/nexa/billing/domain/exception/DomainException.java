package com.nexa.billing.domain.exception;

/**
 * 计费与钱包域领域异常基类。
 *
 * <p>计费与额度（基础倍率计费 / 充值 / 兑换码 / 订阅 / 利润看板 / 公开价格页，
 * F-2038~F-2048 + F-6007~F-6009）所有业务规则违反统一继承本类，携带稳定业务错误码
 * （{@link #code()}），供接口层翻译成 HTTP 状态码 + 响应 message（对齐 openapi 的 ErrorResponse）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.account /
 * com.nexa.channel / com.nexa.deployment 同构，各 bounded context 维护各自的领域异常基类，
 * 互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code REDEMPTION_INVALID}），供接口层映射与前端识别
     * @param message 面向开发者/可读的错误描述
     */
    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
