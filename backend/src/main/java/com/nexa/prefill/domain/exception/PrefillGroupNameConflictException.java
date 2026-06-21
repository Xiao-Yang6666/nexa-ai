package com.nexa.prefill.domain.exception;

/**
 * 预填分组名称冲突异常（→ 409，对齐 openapi {@code POST/PUT /api/prefill_group} 的
 * 「同 type 下 name 冲突 / 新 name 与同 type 他组冲突 → prefill group name conflict」）。
 *
 * <p>领域规则来源：PRD 模块十五 §14「名称冲突校验」——预填分组在同一 type 维度下名称需唯一
 * （创建时同 type 已存在同名 → 冲突；更新时新 name 命中同 type 的<b>其它</b>组 → 冲突）。
 * 注：DB 层 {@code uk_prefill_name(name)} 是全局唯一兜底，但业务语义按 type 维度校验，故应用层在
 * 入库前显式查重以返回精确的 409 而非依赖 DB 唯一约束抛底层异常。</p>
 */
public final class PrefillGroupNameConflictException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "PREFILL_GROUP_NAME_CONFLICT";

    /**
     * @param name 冲突的名称
     */
    public PrefillGroupNameConflictException(String name) {
        super(CODE, "prefill group name conflict: " + name);
    }
}
