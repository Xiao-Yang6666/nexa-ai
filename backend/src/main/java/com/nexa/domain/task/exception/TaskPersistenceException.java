package com.nexa.domain.task.exception;

import com.nexa.domain.kernel.HttpAwareDomainException;

/**
 * 任务持久化异常（基础设施层 JSON 序列化/反序列化失败 → 500）。
 *
 * <p>privateData/data 等 JSONB 字段与 {@link com.nexa.domain.task.vo.BillingContext} 互转失败时抛出，
 * 保留底层 Jackson 错误链（不吞错，backend-engineer §3.2）。接口层翻译为 500（不泄露内部细节）。</p>
 */
public class TaskPersistenceException extends HttpAwareDomainException {

    public TaskPersistenceException(String message, Throwable cause) {
        super("TASK_PERSISTENCE_ERROR", 500, message, cause);
    }

    /** 不带 cause 的便捷构造（用于自身校验类失败，无外层链需保留）。 */
    public TaskPersistenceException(String message) {
        super("TASK_PERSISTENCE_ERROR", 500, message, null);
    }
}
