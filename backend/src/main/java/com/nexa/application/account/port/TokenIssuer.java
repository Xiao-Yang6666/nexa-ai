package com.nexa.application.account.port;

import com.nexa.domain.account.model.User;

/**
 * 访问令牌签发端口（应用层依赖，基础设施层用 JWT 实现）。
 *
 * <p>登录成功后签发会话令牌（API-ENDPOINTS §1.1 登录建立会话；本切片用无状态 JWT 承载
 * 会话内 id/role）。定义为端口以便用例不直接依赖 jjwt，单测可注入桩。</p>
 */
public interface TokenIssuer {

    /**
     * 为已认证用户签发访问令牌。
     *
     * @param user 已通过密码与状态校验的用户聚合
     * @return 签名后的 JWT 字符串
     */
    String issue(User user);
}
