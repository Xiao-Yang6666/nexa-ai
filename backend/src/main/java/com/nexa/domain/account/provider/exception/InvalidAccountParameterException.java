package com.nexa.domain.account.provider.exception;

import com.nexa.domain.kernel.DomainException;

/**
 * 供应商账号入参非法异常（缺失必填/越界/格式错误，→400）。
 *
 * <p>由账号聚合根/值对象在构造或状态变更点抛出，接口层 {@code ProviderAccountExceptionHandler}
 * 翻译为 400 BadRequest。message 为可读契约文案（不含敏感信息，尤其不回显 credentials）。</p>
 */
public class InvalidAccountParameterException extends DomainException {

    /** @param message 参数非法描述（契约文案，无敏感信息） */
    public InvalidAccountParameterException(String message) {
        super("INVALID_ACCOUNT_PARAMETER", message);
    }
}
