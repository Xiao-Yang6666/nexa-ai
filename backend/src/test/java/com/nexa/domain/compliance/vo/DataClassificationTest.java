package com.nexa.domain.compliance.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataClassification} 单测（纯 JUnit）——F-5016 数据分级登记，分级 → 处置策略映射。
 *
 * <p>验证四级分级的加密/下发/注销处置策略符合 DC-001/DC-003/DC-011。</p>
 */
@DisplayName("数据分级")
class DataClassificationTest {

    @Test
    @DisplayName("凭证级：强制加密、绝不下发、注销物理删除")
    void credential() {
        DataClassification c = DataClassification.CREDENTIAL;
        assertTrue(c.requiresEncryptionAtRest());
        assertTrue(c.isNeverDisclosed());
        assertEquals(DataClassification.DisposalAction.PURGE, c.deactivationDisposal());
    }

    @Test
    @DisplayName("PII 级：不强制加密、注销匿名化")
    void pii() {
        DataClassification c = DataClassification.PII;
        assertFalse(c.requiresEncryptionAtRest());
        assertFalse(c.isNeverDisclosed());
        assertEquals(DataClassification.DisposalAction.ANONYMIZE, c.deactivationDisposal());
    }

    @Test
    @DisplayName("内容级：注销匿名化（解除关联）")
    void content() {
        assertEquals(DataClassification.DisposalAction.ANONYMIZE,
                DataClassification.CONTENT.deactivationDisposal());
    }

    @Test
    @DisplayName("计量级：注销聚合保留")
    void metering() {
        assertEquals(DataClassification.DisposalAction.RETAIN_AGGREGATED,
                DataClassification.METERING.deactivationDisposal());
    }

    @Test
    @DisplayName("敏感度排序：凭证 > PII > 内容 > 计量")
    void sensitivityOrder() {
        assertTrue(DataClassification.CREDENTIAL.sensitivity() > DataClassification.PII.sensitivity());
        assertTrue(DataClassification.PII.sensitivity() > DataClassification.CONTENT.sensitivity());
        assertTrue(DataClassification.CONTENT.sensitivity() > DataClassification.METERING.sensitivity());
    }

    @Test
    @DisplayName("fromCode：大小写不敏感解析，非法代码抛异常")
    void fromCode() {
        assertEquals(DataClassification.PII, DataClassification.fromCode("PII"));
        assertEquals(DataClassification.CREDENTIAL, DataClassification.fromCode(" credential "));
        assertThrows(IllegalArgumentException.class, () -> DataClassification.fromCode("bogus"));
    }
}
