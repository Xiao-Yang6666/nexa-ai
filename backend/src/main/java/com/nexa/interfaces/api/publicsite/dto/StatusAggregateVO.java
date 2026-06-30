package com.nexa.interfaces.api.publicsite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.domain.publicsite.vo.SiteStatus;

/**
 * 营销首页公开状态聚合视图 DTO（对齐 openapi.yaml {@code StatusAggregateVO}，F-4039）。
 *
 * <p><b>客户视图零敏感泄露（产品铁律 + openapi「敏感配置不暴露」）</b>：本 DTO 仅承载公开文案与
 * 「是否启用某能力」的布尔开关，<b>绝不</b>含任何密钥/Token/Client Secret/上游地址/成本利润。
 * 字段名用 {@code @JsonProperty} 显式对齐 openapi 的 snake_case（system_name/footer_html/...）。</p>
 *
 * @param systemName           系统名
 * @param logo                 Logo URL
 * @param footerHtml           页脚 HTML
 * @param registerEnabled      是否开放注册
 * @param emailVerification    是否开启邮箱验证码
 * @param githubOauth          是否启用 GitHub 登录
 * @param discordOauth         是否启用 Discord 登录
 * @param oidcEnabled          是否启用 OIDC 登录
 * @param linuxdoOauth         是否启用 LinuxDO 登录
 * @param wechatLogin          是否启用微信登录
 * @param telegramOauth        是否启用 Telegram 登录
 * @param turnstileCheck       是否启用 Turnstile 人机校验
 * @param theme                主题标识
 * @param checkinEnabled       是否开启签到
 * @param userAgreementEnabled 是否展示用户协议
 * @param privacyPolicyEnabled 是否展示隐私政策
 * @param defaultUseAutoGroup  新用户是否默认自动分组
 */
public record StatusAggregateVO(
        @JsonProperty("system_name") String systemName,
        @JsonProperty("logo") String logo,
        @JsonProperty("footer_html") String footerHtml,
        @JsonProperty("register_enabled") boolean registerEnabled,
        @JsonProperty("email_verification") boolean emailVerification,
        @JsonProperty("github_oauth") boolean githubOauth,
        @JsonProperty("discord_oauth") boolean discordOauth,
        @JsonProperty("oidc_enabled") boolean oidcEnabled,
        @JsonProperty("linuxdo_oauth") boolean linuxdoOauth,
        @JsonProperty("wechat_login") boolean wechatLogin,
        @JsonProperty("telegram_oauth") boolean telegramOauth,
        @JsonProperty("turnstile_check") boolean turnstileCheck,
        @JsonProperty("theme") String theme,
        @JsonProperty("checkin_enabled") boolean checkinEnabled,
        @JsonProperty("user_agreement_enabled") boolean userAgreementEnabled,
        @JsonProperty("privacy_policy_enabled") boolean privacyPolicyEnabled,
        @JsonProperty("default_use_auto_group") boolean defaultUseAutoGroup) {

    /**
     * 从领域状态聚合投影为公开视图 DTO（显式逐字段映射，无任何敏感字段可读取）。
     *
     * @param s 领域状态聚合值对象
     * @return 公开状态视图 DTO
     */
    public static StatusAggregateVO from(SiteStatus s) {
        return new StatusAggregateVO(
                s.systemName(),
                s.logo(),
                s.footerHtml(),
                s.registerEnabled(),
                s.emailVerification(),
                s.githubOauth(),
                s.discordOauth(),
                s.oidcEnabled(),
                s.linuxdoOauth(),
                s.wechatLogin(),
                s.telegramOauth(),
                s.turnstileCheck(),
                s.theme(),
                s.checkinEnabled(),
                s.userAgreementEnabled(),
                s.privacyPolicyEnabled(),
                s.defaultUseAutoGroup());
    }
}
