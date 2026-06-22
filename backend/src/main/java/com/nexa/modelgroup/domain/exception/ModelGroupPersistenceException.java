package com.nexa.modelgroup.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 模型组持久化异常（→ HTTP 500，内部错误不泄露细节）。
 *
 * <p>基础设施层在聚合 ⇄ 实体映射（如模型列表序列化）出错时包装底层异常抛出，保留错误链供日志排查
 * （backend-engineer §3.2 不吞错）。携带稳定错误码 {@code MODEL_GROUP_PERSISTENCE_ERROR}。</p>
 */
public class ModelGroupPersistenceException extends DomainException {

    /**
     * @param message 内部错误描述
     * @param cause   底层异常（保留错误链）
     */
    public ModelGroupPersistenceException(String message, Throwable cause) {
        super("MODEL_GROUP_PERSISTENCE_ERROR", message);
        initCause(cause);
    }
}
