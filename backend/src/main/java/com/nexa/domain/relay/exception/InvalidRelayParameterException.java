package com.nexa.domain.relay.exception;

import com.nexa.common.kernel.HttpAwareDomainException;

/**
 * Relay 入参非法异常（400）。
 *
 * <p>客户端传入参数缺失/格式错（如空 model、空 messages、超长字段）时由领域校验抛出。
 * 不含具体上游/渠道信息，message 安全返回客户。</p>
 */
public class InvalidRelayParameterException extends HttpAwareDomainException {

    public InvalidRelayParameterException(String message) {
        super("INVALID_RELAY_PARAMETER", 400, message);
    }
}
