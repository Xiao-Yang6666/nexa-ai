package com.nexa.domain.deployment.vo;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 容器日志查询条件值对象（F-3056）。
 *
 * <p>不可变、按值相等。聚合「容器日志查询」的全部过滤/分页条件并固化其归一规则
 * （API-ENDPOINTS §10.4 F-3056）：</p>
 * <ul>
 *   <li>{@code limit}：缺省 {@link #DEFAULT_LIMIT}（100），上限 {@link #MAX_LIMIT}（1000），&gt;1000 截断为 1000，
 *       非正回退缺省</li>
 *   <li>{@code level}/{@code stream}/{@code cursor}：可空透传过滤维度</li>
 *   <li>{@code follow}：流式跟随标记（缺省 false）</li>
 *   <li>{@code startTime}/{@code endTime}：RFC3339 时间范围；<b>非法时间字符串忽略</b>（解析失败按未传处理，
 *       不报错）——契约「非法时间字符串→忽略」</li>
 * </ul>
 *
 * <p>用值对象集中归一而非在控制器/上游调用处散落 if：limit 截断、时间宽松解析这类规则只此一处，
 * 杜绝多端点行为漂移（backend-engineer §2.4）。所有字段为只读，时间已规整为 RFC3339 字符串便于
 * 透传上游。</p>
 *
 * @param limit     返回条数（保证 1..1000）
 * @param level     日志级别过滤（可空）
 * @param stream    日志流过滤（如 stdout/stderr，可空）
 * @param cursor    分页游标（可空）
 * @param follow    是否流式跟随
 * @param startTime 起始时间（RFC3339 规范化字符串，可空；非法输入已被忽略为 null）
 * @param endTime   结束时间（RFC3339 规范化字符串，可空；非法输入已被忽略为 null）
 */
public record LogQuery(
        int limit,
        String level,
        String stream,
        String cursor,
        boolean follow,
        String startTime,
        String endTime) {

    /** 日志条数缺省值（契约 F-3056）。 */
    public static final int DEFAULT_LIMIT = 100;

    /** 日志条数上限（契约 F-3056：limit&gt;1000→截断 1000）。 */
    public static final int MAX_LIMIT = 1000;

    /**
     * 从原始 query 参数构造并归一日志查询条件。
     *
     * <p>归一规则见类注释。时间解析为宽松模式：能按 RFC3339（{@link OffsetDateTime}）解析则规范化保留，
     * 否则静默忽略（契约「非法时间字符串→忽略」，不向客户端报错）。</p>
     *
     * @param rawLimit  原始 limit（可空）
     * @param level     日志级别（可空）
     * @param stream    日志流（可空）
     * @param cursor    游标（可空）
     * @param follow    是否跟随（可空，null=false）
     * @param rawStart  原始起始时间字符串（可空）
     * @param rawEnd    原始结束时间字符串（可空）
     * @return 归一后的日志查询条件
     */
    public static LogQuery of(Integer rawLimit, String level, String stream, String cursor,
                              Boolean follow, String rawStart, String rawEnd) {
        return new LogQuery(
                normalizeLimit(rawLimit),
                blankToNull(level),
                blankToNull(stream),
                blankToNull(cursor),
                follow != null && follow,
                normalizeTime(rawStart).orElse(null),
                normalizeTime(rawEnd).orElse(null));
    }

    /**
     * limit 归一：null/非正→缺省 100；&gt;1000→截断 1000。
     *
     * @param rawLimit 原始 limit
     * @return 归一后的 limit（1..1000）
     */
    private static int normalizeLimit(Integer rawLimit) {
        if (rawLimit == null || rawLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(rawLimit, MAX_LIMIT);
    }

    /**
     * RFC3339 时间宽松解析：解析成功返回规范化字符串，失败/空白返回空（忽略）。
     *
     * @param raw 原始时间字符串
     * @return 规范化时间字符串（Optional 空=忽略）
     */
    private static Optional<String> normalizeTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            // 按 RFC3339（带偏移）解析并回写规范化形式；非法格式落入 catch 被忽略。
            return Optional.of(OffsetDateTime.parse(raw.trim()).toString());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
