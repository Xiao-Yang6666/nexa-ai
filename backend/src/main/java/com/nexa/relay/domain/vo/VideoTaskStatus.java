package com.nexa.relay.domain.vo;

/**
 * 视频任务状态枚举（RL-5 终态校验用，对齐 DB-SCHEMA §9 Task.Status）。
 *
 * <p>领域规则来源：DB-SCHEMA §9 + prd-relay RL-5 §5 Task.Status 枚举（TaskStatus: NOT_START/SUBMITTED/
 * QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN）。只有 SUCCESS 终态才允许取内容。</p>
 */
public enum VideoTaskStatus {

    NOT_START,
    SUBMITTED,
    QUEUED,
    IN_PROGRESS,
    FAILURE,
    SUCCESS,
    UNKNOWN;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public static VideoTaskStatus fromWire(String wire) {
        if (wire == null) return UNKNOWN;
        try {
            return valueOf(wire.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
