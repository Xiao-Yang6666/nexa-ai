package com.nexa.common.security.domain.exception;

/**
 * 越权拒绝（已认证但权限级别/范围不足）领域异常。
 *
 * <p>语义：操作者身份已解析成功，但其角色不满足端点要求的最低鉴权级别
 * （AdminAuth 要求 Role≥admin、RootAuth 要求 Role==root），或访问了不属于自己的 self-scope 资源。
 * 对齐 openapi {@code ForbiddenError}（F-5031 越权路由返回 403），由接口层翻译为 HTTP 403。</p>
 *
 * <p>领域规则来源：ROLE-PERMISSION-MATRIX §3「self-scope（🟡）越权访问他人资源返回 403」、
 * §6「F-5031 三级系统角色鉴权」「F-5032 self-scope 资源越权防护」。与
 * {@link AuthenticationRequiredException}（未认证→401）区分。</p>
 *
 * <p>安全：message 不回显目标资源归属/他人账号细节（避免账号枚举）。</p>
 */
public class AccessDeniedException extends SecurityException {

    /** 稳定业务错误码。 */
    public static final String CODE = "ACCESS_DENIED";

    /**
     * 以默认稳定提示构造。
     */
    public AccessDeniedException() {
        super(CODE, "access denied: insufficient privilege");
    }

    /**
     * 以自定义稳定提示构造（不得回显可枚举的资源/账号细节）。
     *
     * @param message 面向客户的稳定提示
     */
    public AccessDeniedException(String message) {
        super(CODE, message);
    }
}
