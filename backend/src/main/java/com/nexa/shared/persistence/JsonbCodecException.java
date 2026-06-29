package com.nexa.shared.persistence;

/**
 * JSONB 字段编解码失败异常（基础设施层）。
 *
 * <p>{@link JsonbCodec} 序列化/反序列化失败时抛出。表示「持久化层 JSON 数据损坏 / 编程错误」
 * 这一可观测的基础设施异常——既非领域业务规则违反（不属任一 bounded context 的 DomainException），
 * 也不该被吞掉。保留 {@code cause} 错误链便于排障（backend-engineer §3.2 不吞错）。</p>
 *
 * <p>由全局 {@code GlobalApiExceptionHandler} 兜底翻译为 500（数据损坏属服务端错误，非客户端可纠正）。</p>
 */
public class JsonbCodecException extends RuntimeException {

    /**
     * @param message 失败描述（含目标类型名，绝不含字段明文值——可能是凭证/敏感 JSON）
     * @param cause   底层 Jackson 异常（保留错误链）
     */
    public JsonbCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
