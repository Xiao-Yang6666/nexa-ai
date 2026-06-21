package com.nexa.publicsite.domain.vo;

/**
 * 营销首页公开站点状态聚合（不可变值对象，F-4039）。
 *
 * <p>对齐 openapi {@code StatusAggregateView}：系统名/Logo/页脚 + 各登录方式开关 + 主题 + 签到/协议开关等。
 * 这是<b>面向匿名公众</b>的状态聚合，前端据此渲染首页与登录入口（哪些登录方式可见、是否开放注册等）。</p>
 *
 * <p><b>零敏感泄露铁律（产品铁律 + openapi「敏感配置不暴露」）</b>：本 VO 只承载「是否启用某能力」的<b>布尔开关</b>
 * 与公开文案（系统名/logo/footer），<b>绝不</b>包含任何密钥/Token/Client Secret/上游地址/成本利润等敏感配置。
 * 各 {@code xxxOauth} 字段只表达「该登录方式是否对外可用」，不含其 token/secret——这是公开端点，必须从字段
 * 层面杜绝敏感项进入（backend-engineer §3.4 安全默认 + 客户视图零泄露）。</p>
 *
 * <p>值对象（backend-engineer §2.4）：不可变、按值语义、构造即定型。用 record 表达，字段全为开关/文案，
 * 不变量「不含敏感字段」由<b>字段集合本身</b>保证（根本没有 secret 字段可填）。</p>
 *
 * @param systemName           系统名（公开文案）
 * @param logo                 Logo URL（公开文案）
 * @param footerHtml           页脚 HTML（公开文案）
 * @param registerEnabled      是否开放注册
 * @param emailVerification    是否开启邮箱验证码
 * @param githubOauth          是否启用 GitHub 登录
 * @param discordOauth         是否启用 Discord 登录
 * @param oidcEnabled          是否启用 OIDC 登录
 * @param linuxdoOauth         是否启用 LinuxDO 登录
 * @param wechatLogin          是否启用微信登录
 * @param telegramOauth        是否启用 Telegram 登录
 * @param turnstileCheck       是否启用 Turnstile 人机校验
 * @param theme                主题标识（default/classic 等）
 * @param checkinEnabled       是否开启签到
 * @param userAgreementEnabled 是否展示用户协议
 * @param privacyPolicyEnabled 是否展示隐私政策
 * @param defaultUseAutoGroup  新用户是否默认自动分组
 */
public record SiteStatus(
        String systemName,
        String logo,
        String footerHtml,
        boolean registerEnabled,
        boolean emailVerification,
        boolean githubOauth,
        boolean discordOauth,
        boolean oidcEnabled,
        boolean linuxdoOauth,
        boolean wechatLogin,
        boolean telegramOauth,
        boolean turnstileCheck,
        String theme,
        boolean checkinEnabled,
        boolean userAgreementEnabled,
        boolean privacyPolicyEnabled,
        boolean defaultUseAutoGroup) {

    /**
     * 紧凑构造器：文案字段为 null 时归一为空串（公开端点返回稳定形状，前端无需判 null）。
     */
    public SiteStatus {
        systemName = systemName == null ? "" : systemName;
        logo = logo == null ? "" : logo;
        footerHtml = footerHtml == null ? "" : footerHtml;
        theme = theme == null ? "" : theme;
    }
}
