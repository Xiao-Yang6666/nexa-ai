package com.nexa.account.interfaces.api.dto;

import com.nexa.account.domain.model.User;

/**
 * 用户管理端视图 DTO（对齐 openapi.yaml {@code UserAdminView}，AdminAuth 端点返回）。
 *
 * <p><b>管理端可见全字段，但仍杜绝敏感泄露（产品铁律）</b>：本 DTO 含 {@code remark}/{@code inviter_id}
 * 等管理端专属字段（区别于客户 {@link UserView}），但<b>绝不</b>包含 {@code password}/{@code passwordHash}
 * ——敏感字段在投影时<b>根本不读取</b>，从源头杜绝下发（backend-engineer §6 Pitfall 8）。</p>
 *
 * <p>字段对齐 openapi {@code UserAdminView}：id/username/email/role/status/quota/used_quota/
 * request_count/group/remark/aff_code/inviter_id/last_login_at/created_at。OAuth 绑定 id 等
 * 字段本切片尚未落地，缺省给 null/0，留待后续账号 wave 补齐。</p>
 *
 * @param id           用户 id
 * @param username     用户名
 * @param displayName  展示名（可空）
 * @param email        邮箱（可空）
 * @param role         角色编码（1=common,10=admin,100=root）
 * @param status       状态编码（1=启用，其它=禁用）
 * @param quota        当前额度
 * @param usedQuota    已用额度
 * @param requestCount 请求计数
 * @param group        用户分组（F-1013）
 * @param remark       管理员备注（F-1014，仅管理端可见，可空）
 * @param affCode      个人邀请码
 * @param inviterId    邀请人 id（无则 0）
 * @param lastLoginAt  最近登录时间（epoch 秒）
 * @param createdAt    创建时间（epoch 秒）
 */
public record AdminUserView(
        Long id,
        String username,
        String displayName,
        String email,
        int role,
        int status,
        long quota,
        long usedQuota,
        long requestCount,
        String group,
        String remark,
        String affCode,
        long inviterId,
        long lastLoginAt,
        long createdAt) {

    /**
     * 从用户聚合投影为管理端视图 DTO。
     *
     * <p>显式逐字段映射而非反射拷贝——确保新增聚合字段不会「默认泄露」，且密码哈希在此处
     * 根本不读取（{@link User#passwordHash()} 不被调用）。</p>
     *
     * @param user 目标用户聚合
     * @return 管理端视图 DTO（含 remark/inviter_id，但无任何密码字段）
     */
    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.id(),
                user.username().value(),
                user.displayName(),
                user.email() == null ? null : user.email().value(),
                user.role().code(),
                user.status().code(),
                user.quota(),
                user.usedQuota(),
                user.requestCount(),
                user.group(),
                user.remark(),
                user.affCode(),
                user.inviterId(),
                user.lastLoginAt(),
                user.createdAt());
    }
}
