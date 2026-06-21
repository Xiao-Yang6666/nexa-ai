package com.nexa.token.domain.exception;

/**
 * 令牌域领域异常基类。
 *
 * <p>令牌管理（API 令牌 CRUD / status_only 更新 / 批量删 / 列表搜索 / 取明文 key / 用量 /
 * 模型限制 / IP 白名单 / 分组 / 跨组重试 / 端点级减法约束，F-3001~F-3012）所有业务规则违反统一
 * 继承本类，携带稳定业务错误码（{@link #code()}），供接口层翻译成 HTTP 状态码 + 响应 message
 * （对齐 openapi 的 ErrorResponse）。</p>
 *
 * <p>设计依据：backend-engineer §3.2「领域错误用明确错误类型/错误码，不靠 panic 控流」；
 * domain 层零框架依赖，本异常为纯 Java RuntimeException 派生（与 com.nexa.channel /
 * com.nexa.billing 同构，各 bounded context 维护各自的领域异常基类，互不耦合）。</p>
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * @param code    稳定业务错误码（如 {@code INVALID_TOKEN_PARAMETER}），供接口层映射与前端识别
     * @param message 面向开发者/可读的错误描述（绝不含令牌明文 key）
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
