package com.nexa.compliance.domain.vo;

import com.nexa.compliance.domain.exception.InvalidRetentionPolicyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PromptRetentionPolicy} 单测（纯 JUnit，零 Spring）——F-5017 prompt 留存开关与保留期。
 *
 * <p>按正常/边界/异常组织（backend-engineer §3.3）：默认关闭、开启天数校验、超期判定。</p>
 */
@DisplayName("prompt 留存策略")
class PromptRetentionPolicyTest {

    @Test
    @DisplayName("默认关闭留存（DC-005 默认不留正文）")
    void defaultDisabled() {
        PromptRetentionPolicy p = PromptRetentionPolicy.disabled();
        assertFalse(p.isEnabled());
    }

    @Test
    @DisplayName("of(false, n)=关闭，of(true, n)=开启并校验天数")
    void factoryOf() {
        assertFalse(PromptRetentionPolicy.of(false, 999).isEnabled(), "关闭时忽略天数");
        PromptRetentionPolicy on = PromptRetentionPolicy.of(true, 7);
        assertTrue(on.isEnabled());
        assertEquals(7, on.retentionDays());
    }

    @Test
    @DisplayName("开启留存：天数 <=0 或超上限 → 抛异常")
    void enabledInvalidDays() {
        assertThrows(InvalidRetentionPolicyException.class, () -> PromptRetentionPolicy.enabledFor(0));
        assertThrows(InvalidRetentionPolicyException.class, () -> PromptRetentionPolicy.enabledFor(-1));
        assertThrows(InvalidRetentionPolicyException.class,
                () -> PromptRetentionPolicy.enabledFor(PromptRetentionPolicy.MAX_RETENTION_DAYS + 1));
    }

    @Test
    @DisplayName("开启留存：边界 1 天与上限天数合法")
    void enabledBoundaryDays() {
        assertEquals(1, PromptRetentionPolicy.enabledFor(1).retentionDays());
        assertEquals(PromptRetentionPolicy.MAX_RETENTION_DAYS,
                PromptRetentionPolicy.enabledFor(PromptRetentionPolicy.MAX_RETENTION_DAYS).retentionDays());
    }

    @Test
    @DisplayName("超期判定：关闭留存时任何正文都判超期（应清理遗留）")
    void disabledAlwaysExpired() {
        PromptRetentionPolicy p = PromptRetentionPolicy.disabled();
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        assertTrue(p.isExpired(now, now), "刚写入也判超期");
    }

    @Test
    @DisplayName("超期判定：开启留存时按保留期判定")
    void enabledExpiry() {
        PromptRetentionPolicy p = PromptRetentionPolicy.enabledFor(30);
        Instant recorded = Instant.parse("2026-05-01T00:00:00Z");
        assertFalse(p.isExpired(recorded, Instant.parse("2026-05-20T00:00:00Z")), "19 天内未超期");
        assertTrue(p.isExpired(recorded, Instant.parse("2026-06-15T00:00:00Z")), "45 天已超期");
    }
}
