package com.nexa.domain.modelgroup.vo;

import com.nexa.domain.modelgroup.exception.InvalidModelGroupParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型组值对象单测（{@link AccessPolicy}/{@link AccessSubjectType}/{@link ModelGroupStatus}/{@link ModelNames}）。
 */
@DisplayName("模型组值对象")
class ModelGroupValueObjectsTest {

    @Test
    @DisplayName("AccessPolicy 大小写不敏感解析，非法抛异常")
    void accessPolicy() {
        assertEquals(AccessPolicy.PUBLIC, AccessPolicy.fromWire(" public "));
        assertEquals(AccessPolicy.AUTO_LEVEL, AccessPolicy.fromWire("auto_level"));
        assertThrows(InvalidModelGroupParameterException.class, () -> AccessPolicy.fromWire("x"));
        assertThrows(InvalidModelGroupParameterException.class, () -> AccessPolicy.fromWire(null));
    }

    @Test
    @DisplayName("AccessSubjectType 解析")
    void subjectType() {
        assertEquals(AccessSubjectType.USER, AccessSubjectType.fromWire("USER"));
        assertEquals(AccessSubjectType.TOKEN, AccessSubjectType.fromWire(" token "));
        assertThrows(InvalidModelGroupParameterException.class, () -> AccessSubjectType.fromWire("group"));
    }

    @Test
    @DisplayName("ModelGroupStatus 脏码归并禁用，仅 1 为启用")
    void status() {
        assertEquals(ModelGroupStatus.ENABLED, ModelGroupStatus.fromCode(1));
        assertEquals(ModelGroupStatus.DISABLED, ModelGroupStatus.fromCode(2));
        assertEquals(ModelGroupStatus.DISABLED, ModelGroupStatus.fromCode(99)); // 脏码归并禁用
        assertTrue(ModelGroupStatus.ENABLED.isEnabled());
        assertFalse(ModelGroupStatus.DISABLED.isEnabled());
    }

    @Test
    @DisplayName("ModelNames 去空白去重保序，contains 判定")
    void modelNames() {
        ModelNames m = ModelNames.of(Arrays.asList("gpt-4o", " gpt-4o ", null, " claude ", ""));
        assertEquals(List.of("gpt-4o", "claude"), m.values());
        assertEquals(2, m.size());
        assertTrue(m.contains("gpt-4o"));
        assertTrue(m.contains(" claude "));
        assertFalse(m.contains("gemini"));
        assertTrue(ModelNames.of(null).isEmpty());
    }
}
