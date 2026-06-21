package com.nexa.account.application.port;

import com.nexa.account.domain.vo.OAuthProvider;

/**
 * 第三方 OAuth 客户端端口（应用层定义，基础设施层各 provider 实现）。
 *
 * <p>DDD 依赖倒置 + 策略抽象：各 provider（GitHub/Discord/OIDC/LinuxDO）的「授权码换 token、
 * 用 token 拉 userinfo」流程协议各异（token 端点、userinfo 端点、字段映射不同），但<b>形状一致</b>：
 * {@code code → OAuthUserInfo}。OAuth 登录用例只依赖本端口，差异由 infrastructure 各实现类封装
 * （{@code GitHubOAuthClient/DiscordOAuthClient/OidcOAuthClient/LinuxDoOAuthClient}），
 * 用例无需感知 provider 细节（backend-engineer §2.3 + 策略模式）。</p>
 *
 * <p>对齐 openapi F-1016~1020 各 OAuth 回调端点：回调带回 {@code code}，后端据此完成换 token
 * 与拉 userinfo。多实现按 {@link #provider()} 注册，用例/工厂据 provider 选择实现。</p>
 */
public interface OAuthClient {

    /**
     * @return 本实现服务的 provider（供工厂据此路由分发）
     */
    OAuthProvider provider();

    /**
     * 用授权码换取并归一化第三方用户信息。
     *
     * <p>实现负责：① 用 {@code code} 向 provider token 端点换取 access_token；
     * ② 用 access_token 向 userinfo 端点拉取用户信息；③ 把异构 userinfo 归一化为
     * {@link OAuthUserInfo}（至少含非空 {@code providerUserId}）。网络/解析错误应 wrap 带
     * provider 上下文向上抛（不吞错），由上层翻译为对用户的失败响应。</p>
     *
     * @param code 第三方回调带回的授权码
     * @return 归一化后的第三方用户信息
     */
    OAuthUserInfo exchangeCodeForUserInfo(String code);
}
