package com.nexa.interfaces.account.api.dto;

import com.nexa.domain.account.model.User;

/**
 * 用户客户视图 DTO（对齐 openapi.yaml {@code UserVO}，登录返回的 data）。
 *
 * <p><b>客户视图零敏感泄露（产品铁律）</b>：本 DTO <b>绝不</b>包含 password / passwordHash /
 * access_token，也不含成本/利润/上游真实模型/渠道等内部字段。仅下发本人可见的账号概览字段
 * （openapi UserVO 子集）。本切片只回显注册/登录已落地的字段，其余（display_name/group/
 * used_quota/oauth_id 等）后续 wave 补齐，缺省给空/0。</p>
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
public record UserVO(
        Long id,
        String username,
        int role,
        int status,
        long quota,
        String affCode,
        String email,
        long lastLoginAt) {

    /**
     * 从用户聚合投影为客户视图 DTO。
     *
     * <p>显式逐字段映射而非反射拷贝——确保新增聚合字段不会"默认泄露"到客户视图，
     * 敏感字段（passwordHash）在此处<b>根本不读取</b>，从源头杜绝下发。</p>
     *
     * @param user 已认证用户聚合
     * @return 客户视图 DTO（无任何敏感字段）
     */
    public static UserVO from(User user) {
        return new UserVO(
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
