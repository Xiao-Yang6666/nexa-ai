package com.nexa.domain.relay.vo;

/**
 * Log Type 枚举（对齐 DB-SCHEMA §5 / DATA-MODEL §5，值固定）。
 *
 * <p>领域规则来源：DB-SCHEMA §5 注释行「枚举 Log Type（int，值固定）」。</p>
 */
public enum LogType {

    UNKNOWN(0),
    TOPUP(1),
    CONSUME(2),
    MANAGE(3),
    SYSTEM(4),
    ERROR(5),
    REFUND(6),
    LOGIN(7);

    private final int code;

    LogType(int code) {
        this.code = code;
    }

    public int code() { return code; }

    public static LogType fromCode(int code) {
        for (LogType t : values()) {
            if (t.code == code) return t;
        }
        return UNKNOWN;
    }
}
