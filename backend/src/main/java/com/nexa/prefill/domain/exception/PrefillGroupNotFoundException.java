package com.nexa.prefill.domain.exception;

/**
 * 预填分组不存在异常（→ 404，对齐 openapi {@code PUT/DELETE /api/prefill_group} 的
 * 「id 不存在 → prefill group not found」）。
 *
 * <p>触发场景：更新/软删除时按 id 未命中（含已被软删除的行，{@code @SQLRestriction} 已过滤）。
 * 领域规则来源：PRD 模块十五 §14 预填分组更新/软删除——目标 id 不存在视为 404。</p>
 */
public final class PrefillGroupNotFoundException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PREFILL_GROUP_NOT_FOUND";

    /**
     * @param id 未命中的预填分组 id
     */
    public PrefillGroupNotFoundException(long id) {
        super(CODE, "prefill group not found: id=" + id);
    }
}
