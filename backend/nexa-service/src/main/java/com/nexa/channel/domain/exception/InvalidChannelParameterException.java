package com.nexa.channel.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 渠道入参非法异常（缺失必填/越界/格式错误，→400）。
 *
 * <p>领域规则来源：PRD 模块五 CH-1 渠道 CRUD 入参校验、F-2016。由聚合根/值对象在构造或
 * 状态变更点抛出，接口层 {@code ChannelExceptionHandler} 翻译为 400 BadRequest。
 * message 为可读的契约文案（不含敏感信息，尤其不回显 key）。</p>
 */
public class InvalidChannelParameterException extends DomainException {

    /** @param message 参数非法描述（契约文案，无敏感信息） */
    public InvalidChannelParameterException(String message) {
        super("INVALID_CHANNEL_PARAMETER", message);
    }
}
