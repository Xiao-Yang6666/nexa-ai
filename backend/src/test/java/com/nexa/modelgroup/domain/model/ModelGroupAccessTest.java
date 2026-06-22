package com.nexa.modelgroup.domain.model;

import com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ModelGroupAccess} 授权聚合根单测（纯 JUnit）。
 *
 * <p>覆盖 grant 工厂的不变量校验（modelGroupId/subjectId 必正、subjectType 非空）与重建。</p>
 */
@DisplayName("ModelGroupAccess 授权聚合根")
class ModelGroupAccessTest {

    private static final long NOW = 1_700_000_000L;

    @Test
    @DisplayName("grant 合法 → id 为 null，字段装配正确")
    void validGrant() {
        ModelGroupAccess a = ModelGroupAccess.grant(5L, AccessSubjectType.USER, 99L, NOW);
        assertNull(a.id());
        assertEquals(5L, a.modelGroupId());
        assertEquals(AccessSubjectType.USER, a.subjectType());
        assertEquals(99L, a.subjectId());
        assertEquals(NOW, a.createdTime());
    }

    @Test
    @DisplayName("modelGroupId <= 0 → 抛异常")
    void invalidGroupId() {
        assertThrows(InvalidModelGroupParameterException.class,
                () -> ModelGroupAccess.grant(0L, AccessSubjectType.TOKEN, 1L, NOW));
    }

    @Test
    @DisplayName("subjectId <= 0 → 抛异常")
    void invalidSubjectId() {
        assertThrows(InvalidModelGroupParameterException.class,
                () -> ModelGroupAccess.grant(1L, AccessSubjectType.TOKEN, -1L, NOW));
    }

    @Test
    @DisplayName("subjectType 为 null → 抛异常")
    void nullSubjectType() {
        assertThrows(InvalidModelGroupParameterException.class,
                () -> ModelGroupAccess.grant(1L, null, 1L, NOW));
    }

    @Test
    @DisplayName("rehydrate 装配不触发校验")
    void rehydrate() {
        ModelGroupAccess a = ModelGroupAccess.rehydrate(3L, 5L, AccessSubjectType.TOKEN, 7L, NOW);
        assertEquals(3L, a.id());
        assertEquals(AccessSubjectType.TOKEN, a.subjectType());
    }
}
