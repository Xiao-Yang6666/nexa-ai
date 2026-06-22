package com.nexa.routing.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 亲和缓存/规则持久化异常（基础设施层数据损坏或序列化失败时抛出，F-2029~F-2033）。
 *
 * <p>领域规则来源：backend-engineer §3.2「不吞错」。JSONB（key_sources/pass_headers）序列化/反序列化
 * 失败、缓存读写底层故障等属可观测异常，wrap 原始 cause 保留错误链，接口层翻译为 502（本服务正常但
 * 持久化/数据层故障）。message 不含任何会话键明文/凭证。</p>
 */
public class AffinityPersistenceException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "AFFINITY_PERSISTENCE_ERROR";

    /**
     * @param message 错误描述（不含敏感数据）
     * @param cause   原始异常（保留错误链）
     */
    public AffinityPersistenceException(String message, Throwable cause) {
        super(CODE, message);
        initCause(cause);
    }
}
