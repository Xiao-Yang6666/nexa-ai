package com.nexa.task.domain.vo;

/**
 * 任务状态枚举（对齐 DB-SCHEMA §9 Task.Status，PRD AT-1 状态机）。
 *
 * <p>领域规则：NOT_START→SUBMITTED→QUEUED→IN_PROGRESS→SUCCESS/FAILURE，终态不可再变。
 * UNKNOWN 兜底态（上游无法识别）。状态机合法性由 {@link com.nexa.task.domain.model.Task#canTransitionTo}
 * 守护。</p>
 */
public enum TaskStatus {

    /** 初始态（InitTask 落库后）。 */
    NOT_START,
    /** 已提交。 */
    SUBMITTED,
    /** 队列中。 */
    QUEUED,
    /** 进行中。 */
    IN_PROGRESS,
    /** 失败（终态，触发退款）。 */
    FAILURE,
    /** 成功（终态，差额结算）。 */
    SUCCESS,
    /** 无法识别（兜底）。 */
    UNKNOWN;

    /** @return 是否终态（SUCCESS/FAILURE，终态不可再变，PRD AT-1 §规则）。 */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILURE;
    }

    /** @return 是否成功终态。 */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** @return 是否失败终态。 */
    public boolean isFailure() {
        return this == FAILURE;
    }

    /**
     * 从 wire 格式反序列化（DB/openapi 用大写带下划线）。
     *
     * @param wire 线上格式（如 {@code "IN_PROGRESS"}）
     * @return 枚举值（无法识别返回 {@link #UNKNOWN}）
     */
    public static TaskStatus fromWire(String wire) {
        if (wire == null) return UNKNOWN;
        try {
            return valueOf(wire.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** @return wire 格式（大写带下划线，对齐 DB-SCHEMA）。 */
    public String toWire() {
        return this.name();
    }
}
