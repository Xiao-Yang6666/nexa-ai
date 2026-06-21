package com.nexa.routing.infrastructure.selection;

/**
 * 原 StubChannelSelectionAdapter 已由 {@link AbilityBackedChannelSelectionAdapter} 替换（V25 Ability 表）。
 *
 * <p>保留本文件作为迁移记录占位，不再是 Spring Bean；选渠端口唯一实现为
 * {@link AbilityBackedChannelSelectionAdapter}——基于 abilities 表做优先级分层+权重随机。
 *
 * @deprecated 已由 {@link AbilityBackedChannelSelectionAdapter} 替换；本类将在 W3 清理删除。
 */
@Deprecated(forRemoval = true)
public final class StubChannelSelectionAdapter {
    private StubChannelSelectionAdapter() {}
}
