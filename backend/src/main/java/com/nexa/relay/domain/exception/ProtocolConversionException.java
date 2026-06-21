package com.nexa.relay.domain.exception;

/**
 * 协议转换异常（500，IR/序列化失败）。
 *
 * <p>RL-6/RL-8 ParseRequest/SerializeResponse/Stream 互转过程中遇到无法转换的语义（如缺失必填字段）
 * 抛出，接口层翻译为按入站 RelayFormat 构造的错误响应（RL-3 re_fmt）。</p>
 */
public class ProtocolConversionException extends DomainException {

    public ProtocolConversionException(String message) {
        super("PROTOCOL_CONVERSION_FAILED", 500, message);
    }

    public ProtocolConversionException(String message, Throwable cause) {
        super("PROTOCOL_CONVERSION_FAILED", 500, message, cause);
    }
}
