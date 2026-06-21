package com.nexa.telegram.domain.model;

import com.nexa.telegram.domain.exception.InvalidTelegramAuthException;
import com.nexa.telegram.domain.exception.TelegramBindingConflictException;
import com.nexa.telegram.domain.vo.TelegramId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TelegramBinding 单元测试（F-1052 创建不变量 / F-1054 绑定唯一性护栏）。
 */
class TelegramBindingTest {

    private static final TelegramId TG = TelegramId.of("777888999");

    @Test
    void createRequiresPersistedUserId() {
        // userId <= 0 视为未持久化用户，拒绝建绑定（不变量）。
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramBinding.create(0L, TG));
        assertThrows(InvalidTelegramAuthException.class, () -> TelegramBinding.create(-1L, TG));
    }

    @Test
    void createSucceedsForValidUser() {
        TelegramBinding b = TelegramBinding.create(42L, TG);
        assertEquals(42L, b.userId());
        assertEquals("777888999", b.telegramId().value());
    }

    @Test
    void ensureOwnedByPassesForSameUser() {
        TelegramBinding b = TelegramBinding.create(42L, TG);
        // 绑本人 → 幂等通过（同用户重复绑同一 Telegram 不报错）。
        assertDoesNotThrow(() -> b.ensureOwnedBy(42L));
    }

    @Test
    void ensureOwnedByRejectsOtherUser() {
        // 该 Telegram 已绑用户 42；用户 99 再来绑 → 冲突拒绝（F-1054）。
        TelegramBinding existing = TelegramBinding.create(42L, TG);
        assertThrows(TelegramBindingConflictException.class, () -> existing.ensureOwnedBy(99L));
    }
}
