package com.nexa.deployment.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LogQuery} 值对象归一规则单测（纯 JUnit，F-3056）。
 *
 * <p>覆盖契约 F-3056 的归一规则：limit 缺省 100 / 上限 1000 截断 / 非正回退；时间 RFC3339 宽松解析
 * （非法忽略）；level/stream/cursor 空白归 null；follow 缺省 false。按正常/边界/异常组织。</p>
 */
@DisplayName("容器日志查询条件归一")
class LogQueryTest {

    @Test
    @DisplayName("limit：缺省 100")
    void limitDefault() {
        assertEquals(100, LogQuery.of(null, null, null, null, null, null, null).limit());
    }

    @Test
    @DisplayName("limit：非正回退缺省")
    void limitNonPositiveFallback() {
        assertEquals(100, LogQuery.of(0, null, null, null, null, null, null).limit());
        assertEquals(100, LogQuery.of(-5, null, null, null, null, null, null).limit());
    }

    @Test
    @DisplayName("limit：>1000 截断 1000；正常值原样")
    void limitCap() {
        assertEquals(1000, LogQuery.of(5000, null, null, null, null, null, null).limit());
        assertEquals(1000, LogQuery.of(1000, null, null, null, null, null, null).limit());
        assertEquals(250, LogQuery.of(250, null, null, null, null, null, null).limit());
    }

    @Test
    @DisplayName("时间：合法 RFC3339 规范化保留")
    void timeValid() {
        LogQuery q = LogQuery.of(null, null, null, null, null,
                "2026-06-20T10:00:00Z", "2026-06-20T11:30:00+08:00");
        // 实现用 OffsetDateTime.parse(raw).toString() 规范化,整分钟时 ISO 省略秒(10:00:00Z→10:00Z)。
        // 期望值用同一规范化算出,避免依赖字面格式。
        assertEquals(java.time.OffsetDateTime.parse("2026-06-20T10:00:00Z").toString(), q.startTime());
        assertEquals(java.time.OffsetDateTime.parse("2026-06-20T11:30:00+08:00").toString(), q.endTime());
    }

    @Test
    @DisplayName("时间：非法字符串忽略（不报错，按未传处理）")
    void timeInvalidIgnored() {
        LogQuery q = LogQuery.of(null, null, null, null, null, "not-a-date", "2026/06/20");
        assertNull(q.startTime(), "非法起始时间应忽略");
        assertNull(q.endTime(), "非法结束时间应忽略");
    }

    @Test
    @DisplayName("过滤维度：空白归 null；follow 缺省 false")
    void filtersAndFollow() {
        LogQuery q = LogQuery.of(50, "  ", "", "  ", null, null, null);
        assertNull(q.level());
        assertNull(q.stream());
        assertNull(q.cursor());
        assertFalse(q.follow());

        LogQuery q2 = LogQuery.of(50, "error", "stdout", "cur-1", Boolean.TRUE, null, null);
        assertEquals("error", q2.level());
        assertEquals("stdout", q2.stream());
        assertEquals("cur-1", q2.cursor());
        assertTrue(q2.follow());
    }
}
