package com.nexa.domain.publicsite.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SiteStatus 单元测试（F-4039：公开状态聚合的文案空串归一）。
 */
class SiteStatusTest {

    @Test
    void nullTextFieldsNormalizedToEmptyString() {
        // null 文案归一为空串，保证公开端点返回稳定形状（前端无需判 null）。
        SiteStatus s = new SiteStatus(
                null, null, null,
                true, false, false, false, false, false, false, true, false,
                null, true, false, true, false);
        assertEquals("", s.systemName());
        assertEquals("", s.logo());
        assertEquals("", s.footerHtml());
        assertEquals("", s.theme());
        // 布尔开关原样保留（telegramOauth=true 等）。
        assertTrue(s.telegramOauth());
        assertTrue(s.registerEnabled());
        assertTrue(s.privacyPolicyEnabled());
    }
}
