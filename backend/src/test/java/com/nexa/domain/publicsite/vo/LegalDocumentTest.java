package com.nexa.domain.publicsite.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LegalDocument 单元测试（F-4027/F-4028「启用门控 + 空串兜底」领域规则）。
 */
class LegalDocumentTest {

    @Test
    void disabledYieldsEmptyContentEvenIfConfigured() {
        // 未启用：即使配置了原文，对外也恒为空串（BACKLOG T-199/T-200）。
        LegalDocument doc = LegalDocument.publicContent(
                LegalDocumentType.USER_AGREEMENT, false, "<p>some agreement</p>");
        assertEquals("", doc.content());
        assertEquals(LegalDocumentType.USER_AGREEMENT, doc.type());
    }

    @Test
    void enabledButUnsetYieldsEmptyString() {
        // 启用但未设置内容：openapi「未设置为空串」。
        LegalDocument doc = LegalDocument.publicContent(
                LegalDocumentType.PRIVACY_POLICY, true, null);
        assertEquals("", doc.content());
    }

    @Test
    void enabledWithContentYieldsOriginal() {
        LegalDocument doc = LegalDocument.publicContent(
                LegalDocumentType.PRIVACY_POLICY, true, "<p>privacy</p>");
        assertEquals("<p>privacy</p>", doc.content());
    }
}
