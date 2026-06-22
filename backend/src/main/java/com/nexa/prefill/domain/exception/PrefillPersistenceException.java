package com.nexa.prefill.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 预填分组持久化异常（→ 500，基础设施层 JSON 序列化/反序列化 items 失败时的领域语义包装）。
 *
 * <p>触发场景：条目集合与 JSONB 列互转失败（理论上不应发生——条目均为字符串列表；一旦发生
 * 说明数据/编码异常）。按 backend-engineer §3.2「错误必须 wrap 带上下文、不吞错」，基础设施层
 * 捕获 Jackson 异常后包装为本领域异常向上抛，保留错误链，避免裸 RuntimeException 丢上下文。</p>
 */
public final class PrefillPersistenceException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PREFILL_PERSISTENCE_ERROR";

    /**
     * @param message 上下文描述
     * @param cause   底层异常（保留错误链）
     */
    public PrefillPersistenceException(String message, Throwable cause) {
        super(CODE, message);
        initCause(cause);
    }
}
