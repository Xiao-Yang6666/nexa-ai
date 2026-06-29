package com.nexa.domain.log.vo;

/**
 * 日志类型枚举（日志查询 BC 自持，零框架依赖；对齐 DB-SCHEMA §5「枚举 Log Type」值固定）。
 *
 * <p>领域规则来源：DB-SCHEMA §5 / DATA-MODEL §5「{@code 0=Unknown / 1=Topup / 2=Consume /
 * 3=Manage / 4=System / 5=Error / 6=Refund / 7=Login}」。本枚举刻意<b>独立</b>于 relay BC 的
 * {@code com.nexa.domain.relay.vo.LogType}——两个 bounded context 不互相依赖（DDD 上下文解耦），
 * 编码一致、在基础设施边界按 {@code code} 互转。</p>
 *
 * <p>统计与排行只取 {@link #CONSUME}（F-4004/F-4005/F-4010 口径：仅 Type=2 计入 quota/rpm/tpm），
 * 审计写入用 {@link #MANAGE}（F-4011 高危操作）/{@link #LOGIN}（F-4013 登录）。</p>
 */
public enum LogType {

    /** 未知（0，兜底）。 */
    UNKNOWN(0),

    /** 充值（1）。 */
    TOPUP(1),

    /** 消费（2，唯一计入统计/排行的类型）。 */
    CONSUME(2),

    /** 管理/高危操作审计（3，F-4011）。 */
    MANAGE(3),

    /** 系统（4）。 */
    SYSTEM(4),

    /** 错误（5，RL-3 脱敏后写）。 */
    ERROR(5),

    /** 退款（6）。 */
    REFUND(6),

    /** 登录审计（7，F-4013）。 */
    LOGIN(7);

    private final int code;

    LogType(int code) {
        this.code = code;
    }

    /** @return 落库整数编码（与 DB type 列一致） */
    public int code() {
        return code;
    }

    /**
     * 由整数编码还原类型；未知编码归一为 {@link #UNKNOWN}（查询读侧宽容，存量脏数据不抛错）。
     *
     * @param code 类型编码
     * @return 对应类型，未知→UNKNOWN
     */
    public static LogType fromCode(int code) {
        for (LogType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return UNKNOWN;
    }
}
