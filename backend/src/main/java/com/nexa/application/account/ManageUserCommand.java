package com.nexa.application.account;

/**
 * 管理端用户状态管理命令（应用层入参 DTO，F-1010）。
 *
 * <p>对齐 API-ENDPOINTS §1.4 {@code POST /api/user/manage}：{@code id} + {@code action}
 * （{@code enable}/{@code disable}/{@code promote}/{@code demote}/{@code delete}）。
 * {@code operatorRoleCode} 是发起操作的管理员角色编码，用于领域层角色越权护栏（AC-10）。</p>
 *
 * @param targetUserId     被操作的目标用户 id
 * @param action           动作（enable/disable/promote/demote/delete）
 * @param operatorRoleCode 操作者角色编码（1=common,10=admin,100=root）
 */
public record ManageUserCommand(long targetUserId, String action, int operatorRoleCode) {
}
