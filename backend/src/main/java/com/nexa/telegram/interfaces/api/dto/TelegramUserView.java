package com.nexa.telegram.interfaces.api.dto;

import com.nexa.account.domain.model.User;

/**
 * Telegram 登录用户客户视图 DTO（对齐 openapi.yaml {@code UserView}，F-1051 登录返回的 data）。
 *
 * <p><b>客户视图零敏感泄露（产品铁律）</b>：本 DTO <b>绝不</b>包含 password / passwordHash /
 * access_token，也不含成本/利润/上游真实模型/渠道等内部字段，更不含 Bot Token。字段集与
 * {@code com.nexa.account.interfaces.api.dto.UserView} 对齐（同一 openapi UserView 契约）；
 * 本子域单独持一份是为避免接口层跨 BC 强耦合 account 的 interfaces 包。</p>
 *
 * @param id          用户 id
 * @param username    用户名
 * @param role        角色编码（1=common,10=admin,100=root）
 * @param status      状态编码（1=启用，其它=禁用）
 * @param quota       当前额度
 * @param affCode     个人邀请码
 * @param email       邮箱（可空）
 * @param lastLoginAt 最近登录时间（epoch 秒）
 */
public record TelegramUserView(
        Long id,
        String username,
        int role,
        int status,
        long quota,
        String affCode,
        String email,
        long lastLoginAt) {

    /**
     * 从用户聚合投影为客户视图 DTO（显式逐字段映射，敏感字段在此根本不读取）。
     *
     * @param user 登录/建号后的用户聚合
     * @return 客户视图 DTO（无任何敏感字段）
     */
    public static TelegramUserView from(User user) {
        return new TelegramUserView(
                user.id(),
                user.username().value(),
                user.role().code(),
                user.status().code(),
                user.quota(),
                user.affCode(),
                user.email() == null ? null : user.email().value(),
                user.lastLoginAt());
    }
}
