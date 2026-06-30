package com.nexa.shared.security.exception;

/**
 * 未认证（缺少/无效身份凭据）领域异常。
 *
 * <p>语义：请求命中受保护端点（UserAuth/AdminAuth/RootAuth）但<b>无法解析出有效操作者身份</b>
 * ——缺 Bearer/会话凭据、令牌过期、签名非法、声明缺失等。对齐 openapi {@code UnauthorizedError}，
 * 由接口层翻译为 HTTP 401。</p>
 *
 * <p>领域规则来源：ROLE-PERMISSION-MATRIX §3「访客边界——绝不触达资源型接口」+ openapi
 * 各受保护端点 {@code security} 块。与 {@link AccessDeniedException}（已认证但权限不足→403）区分：
 * 本异常是「不知道你是谁」（401），后者是「知道你是谁但你不够格」（403）。</p>
 *
 * <p>安全：message 用稳定通用提示，<b>不</b>回显令牌细节/失败原因子类（避免给攻击者枚举反馈，
 * NFR-S 安全默认）。</p>
 */
public class AuthenticationRequiredException extends SecurityException {

    /** 稳定业务错误码。 */
    public static final String CODE = "AUTHENTICATION_REQUIRED";

    /**
     * 以默认稳定提示构造（不回显内部失败细节）。
     */
    public AuthenticationRequiredException() {
        super(CODE, "authentication required");
    }

    /**
     * 以自定义稳定提示构造（仍不得携带令牌/密钥等敏感细节）。
     *
     * @param message 面向客户的稳定提示
     */
    public AuthenticationRequiredException(String message) {
        super(CODE, message);
    }
}
