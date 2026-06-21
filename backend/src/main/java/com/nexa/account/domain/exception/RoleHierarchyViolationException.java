package com.nexa.account.domain.exception;

/**
 * 角色越权护栏违反异常（管理端用户管理核心领域规则）。
 *
 * <p>领域规则来源：PRD prd-account.md AC-10「ManageUser 角色越权护栏」：
 * <ul>
 *   <li>M3：操作者<b>不可</b>操作 {@code 目标角色 >= 操作者角色} 的用户（同级/更高角色一律拒绝）；</li>
 *   <li>M7：提升动作<b>不可</b>把目标角色提到 {@code >= 操作者角色}（提升越界拒绝）；</li>
 *   <li>F-1009 创建：不可创建 {@code 角色 >= 操作者角色} 的用户（越权创建拒绝）。</li>
 * </ul>
 * 角色优先级 root(100) &gt; admin(10) &gt; common(1)（DB-SCHEMA §1 Role 枚举）。</p>
 *
 * <p>这是<b>纯领域规则</b>，由聚合根 {@link com.nexa.account.domain.model.User} 的管理类充血方法
 * 在内存中守护，不依赖任何框架——可纯单测（backend-engineer §2.1 DDD 铁律）。接口层把本异常
 * 翻译为 403（无权限/越权拒绝态，对齐 AC-10 §6）。</p>
 */
public class RoleHierarchyViolationException extends DomainException {

    /** 稳定业务错误码（接口层映射 403 越权拒绝态）。 */
    public static final String CODE = "ROLE_HIERARCHY_VIOLATION";

    /**
     * @param message 面向开发者的越权场景描述（不回显可枚举的目标账号细节）
     */
    public RoleHierarchyViolationException(String message) {
        super(CODE, message);
    }
}
