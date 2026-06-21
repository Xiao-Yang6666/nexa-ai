package com.nexa.model.domain.exception;

/**
 * 跨 scope 越权写入异常（接口层映射 403，F-6003）。
 *
 * <p>领域规则来源：DB-SCHEMA §18「越权护栏：user 路由写入强制 scope_type=user AND scope_id=:caller_user_id，
 * 禁跨 scope 写」+ ROLE-PERMISSION-MATRIX §3 self-scope 强制过滤。</p>
 */
public class AliasCrossScopeException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "ALIAS_CROSS_SCOPE_DENIED";

    /** @param message 越权说明 */
    public AliasCrossScopeException(String message) {
        super(CODE, message);
    }
}
