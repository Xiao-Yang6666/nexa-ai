package com.nexa.publicsite.infrastructure.config;

import com.nexa.account.application.port.AccountSettings;
import com.nexa.publicsite.application.port.PublicSiteSettings;
import com.nexa.publicsite.domain.vo.SiteStatus;
import com.nexa.telegram.application.port.TelegramSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 公开站点设置端口 {@link PublicSiteSettings} 的配置驱动实现（基础设施层，F-4039/F-4027/F-4028）。
 *
 * <p>从 {@code application.yml} 的 {@code publicsite.*} 前缀读取公开文案/主题/开关，并<b>复用既有 Bean</b>
 * 推导部分状态以保持单一事实来源（DDD §2.3 把配置来源收敛到 infra）：
 * <ul>
 *   <li>{@code registerEnabled}/{@code emailVerification} 取自 {@link AccountSettings}（与注册用例同源，
 *       避免两处配置漂移）。</li>
 *   <li>{@code telegramOauth} 取自 {@link TelegramSettings#isTelegramLoginEnabled()}（与 Telegram 登录用例同源）。</li>
 *   <li>其余登录方式开关（github/discord/oidc/linuxdo/wechat/turnstile）本切片以 {@code publicsite.*}
 *       配置承载（后续接各 OAuth provider 配置时改为各自同源，仅换本实现不动端口）。</li>
 * </ul></p>
 *
 * <p><b>安全铁律</b>：本类<b>只</b>读取/暴露公开安全字段，<b>绝不</b>把任何 OAuth client secret / Bot Token /
 * 上游地址装进 {@link SiteStatus}——这些敏感配置压根不在本类字段里（从字段层面杜绝泄露，
 * openapi「敏感配置不暴露」 + 客户视图零泄露铁律）。</p>
 */
@Component
@ConfigurationProperties(prefix = "publicsite")
public class ConfigPublicSiteSettings implements PublicSiteSettings {

    private final AccountSettings accountSettings;
    private final TelegramSettings telegramSettings;

    // ---- publicsite.* 配置项（公开安全，默认值给最小可用形状） ----

    /** 系统名（公开文案）。 */
    private String systemName = "Nexa";

    /** Logo URL（公开文案）。 */
    private String logo = "";

    /** 页脚 HTML（公开文案）。 */
    private String footerHtml = "";

    /** 主题标识（default/classic 等）。 */
    private String theme = "default";

    /** 是否启用 GitHub 登录。 */
    private boolean githubOauth = false;

    /** 是否启用 Discord 登录。 */
    private boolean discordOauth = false;

    /** 是否启用 OIDC 登录。 */
    private boolean oidcEnabled = false;

    /** 是否启用 LinuxDO 登录。 */
    private boolean linuxdoOauth = false;

    /** 是否启用微信登录。 */
    private boolean wechatLogin = false;

    /** 是否启用 Turnstile 人机校验。 */
    private boolean turnstileCheck = false;

    /** 是否开启签到。 */
    private boolean checkinEnabled = false;

    /** 是否展示用户协议。 */
    private boolean userAgreementEnabled = false;

    /** 用户协议原文（未设置为空串）。 */
    private String userAgreementContent = "";

    /** 是否展示隐私政策。 */
    private boolean privacyPolicyEnabled = false;

    /** 隐私政策原文（未设置为空串）。 */
    private String privacyPolicyContent = "";

    /** 新用户是否默认自动分组。 */
    private boolean defaultUseAutoGroup = false;

    /**
     * @param accountSettings  账号设置（注册/邮箱验证开关同源）
     * @param telegramSettings Telegram 设置（telegram 登录开关同源）
     */
    public ConfigPublicSiteSettings(AccountSettings accountSettings, TelegramSettings telegramSettings) {
        this.accountSettings = accountSettings;
        this.telegramSettings = telegramSettings;
    }

    /** {@inheritDoc} */
    @Override
    public SiteStatus siteStatus() {
        // 装配公开状态聚合：开关类字段同源于既有 Bean / publicsite.* 配置，绝不含敏感凭据。
        return new SiteStatus(
                systemName,
                logo,
                footerHtml,
                accountSettings.isRegisterEnabled(),
                accountSettings.isEmailVerificationEnabled(),
                githubOauth,
                discordOauth,
                oidcEnabled,
                linuxdoOauth,
                wechatLogin,
                telegramSettings.isTelegramLoginEnabled(),
                turnstileCheck,
                theme,
                checkinEnabled,
                userAgreementEnabled,
                privacyPolicyEnabled,
                defaultUseAutoGroup);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUserAgreementEnabled() {
        return userAgreementEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public String userAgreementContent() {
        return userAgreementContent;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivacyPolicyEnabled() {
        return privacyPolicyEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public String privacyPolicyContent() {
        return privacyPolicyContent;
    }

    // ---- setters（@ConfigurationProperties 绑定用） ----

    /** @param systemName 系统名 */
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    /** @param logo Logo URL */
    public void setLogo(String logo) {
        this.logo = logo;
    }

    /** @param footerHtml 页脚 HTML */
    public void setFooterHtml(String footerHtml) {
        this.footerHtml = footerHtml;
    }

    /** @param theme 主题标识 */
    public void setTheme(String theme) {
        this.theme = theme;
    }

    /** @param githubOauth GitHub 登录开关 */
    public void setGithubOauth(boolean githubOauth) {
        this.githubOauth = githubOauth;
    }

    /** @param discordOauth Discord 登录开关 */
    public void setDiscordOauth(boolean discordOauth) {
        this.discordOauth = discordOauth;
    }

    /** @param oidcEnabled OIDC 登录开关 */
    public void setOidcEnabled(boolean oidcEnabled) {
        this.oidcEnabled = oidcEnabled;
    }

    /** @param linuxdoOauth LinuxDO 登录开关 */
    public void setLinuxdoOauth(boolean linuxdoOauth) {
        this.linuxdoOauth = linuxdoOauth;
    }

    /** @param wechatLogin 微信登录开关 */
    public void setWechatLogin(boolean wechatLogin) {
        this.wechatLogin = wechatLogin;
    }

    /** @param turnstileCheck Turnstile 人机校验开关 */
    public void setTurnstileCheck(boolean turnstileCheck) {
        this.turnstileCheck = turnstileCheck;
    }

    /** @param checkinEnabled 签到开关 */
    public void setCheckinEnabled(boolean checkinEnabled) {
        this.checkinEnabled = checkinEnabled;
    }

    /** @param userAgreementEnabled 用户协议展示开关 */
    public void setUserAgreementEnabled(boolean userAgreementEnabled) {
        this.userAgreementEnabled = userAgreementEnabled;
    }

    /** @param userAgreementContent 用户协议原文 */
    public void setUserAgreementContent(String userAgreementContent) {
        this.userAgreementContent = userAgreementContent;
    }

    /** @param privacyPolicyEnabled 隐私政策展示开关 */
    public void setPrivacyPolicyEnabled(boolean privacyPolicyEnabled) {
        this.privacyPolicyEnabled = privacyPolicyEnabled;
    }

    /** @param privacyPolicyContent 隐私政策原文 */
    public void setPrivacyPolicyContent(String privacyPolicyContent) {
        this.privacyPolicyContent = privacyPolicyContent;
    }

    /** @param defaultUseAutoGroup 新用户默认自动分组开关 */
    public void setDefaultUseAutoGroup(boolean defaultUseAutoGroup) {
        this.defaultUseAutoGroup = defaultUseAutoGroup;
    }
}
