package com.nexa.routing.domain.exception;

/**
 * 路由域（亲和缓存 + 跨分组重试）领域异常基类。
 *
 * <p>W2 选渠中间件子域（会话亲和键提取/渠道粘连/header 透传/SkipRetryOnFailure/auto 分组逐组耗尽
 * 切换/令牌级跨组重试/全局重试次数，F-2029~F-2037，PRD CH-4/CH-5）所有业务规则违反统一继承本类，
 * 携带稳定业务错误码（{@link #code()}），供接口层翻译成 HTTP 状态码 + 响应 message
 * （对齐 openapi ErrorResponse）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.account /
 * com.nexa.channel 同构，各 bounded context 维护各自的领域异常基类，互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code INVALID_AFFINITY_PARAMETER}），供接口层映射与前端识别
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
