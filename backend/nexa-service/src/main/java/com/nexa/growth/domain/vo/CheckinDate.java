package com.nexa.growth.domain.vo;

import com.nexa.growth.domain.exception.InvalidCheckinSettingException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * 签到日期值对象（不可变、按值相等）。
 *
 * <p>对齐 DB-SCHEMA §12 {@code checkin_date varchar(10)} 格式 {@code YYYY-MM-DD}（复合唯一索引
 * {@code idx_user_checkin_date} 的一部分）。把「合法日期格式」封装为值对象避免裸字符串散落，
 * 并保证日级状态机的「当日」语义一致（PRD GR-1 跨日 0 点复位）。</p>
 *
 * <p>backend-engineer §2.4：日期是强语义量，做值对象而非裸 String。</p>
 */
public final class CheckinDate {

    /** 落库/对外统一格式 {@code YYYY-MM-DD}（DB-SCHEMA §12）。 */
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LocalDate value;

    private CheckinDate(LocalDate value) {
        this.value = value;
    }

    /**
     * 由 {@link LocalDate} 构造。
     *
     * @param date 本地日期（非空）
     * @return 签到日期值对象
     */
    public static CheckinDate of(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return new CheckinDate(date);
    }

    /**
     * 解析 {@code YYYY-MM-DD} 字符串（持久化重建用）。
     *
     * @param text 日期串（{@code YYYY-MM-DD}）
     * @return 签到日期值对象
     * @throws InvalidCheckinSettingException 格式非法（防御式，重建脏数据时不静默吞）
     */
    public static CheckinDate parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidCheckinSettingException("checkin_date must not be blank");
        }
        try {
            return new CheckinDate(LocalDate.parse(text.trim(), FORMAT));
        } catch (DateTimeParseException ex) {
            // 不吞错：脏日期串是数据迁移/写入 bug 的信号，向上抛带原文。
            throw new InvalidCheckinSettingException("invalid checkin_date format, expected yyyy-MM-dd: " + text);
        }
    }

    /** @return 底层本地日期 */
    public LocalDate toLocalDate() {
        return value;
    }

    /** @return {@code YYYY-MM-DD} 落库/下发串 */
    public String toWire() {
        return value.format(FORMAT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckinDate that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return toWire();
    }
}
