package com.nexa.application.publicsite.port;

import com.nexa.domain.publicsite.vo.SiteStatus;

/**
 * 公开站点设置端口（应用层依赖，基础设施/配置层实现，F-4039/F-4027/F-4028）。
 *
 * <p>承载营销首页公开状态聚合所需的系统设置（系统名/logo/页脚/各登录方式开关/主题/签到与协议开关等）
 * 及法律文本的「内容 + 启用开关」。定义为端口而非直接读 Spring 配置，便于用例单测注入桩、并把
 * 「配置来源」收敛到 infra（DDD §2.3，backend-engineer §3.4 配置外置）。后续接 §15 动态系统设置时
 * 改为 DB 读取，仅换实现不动应用层签名。</p>
 *
 * <p><b>安全</b>：本端口<b>只</b>暴露公开安全的开关与文案——实现层负责把敏感配置（OAuth client secret /
 * Bot Token / 上游地址等）<b>挡在端口之外</b>，绝不经此端口流向公开端点（openapi「敏感配置不暴露」）。</p>
 */
public interface PublicSiteSettings {

    /**
     * 读取营销首页公开状态聚合（F-4039）。
     *
     * <p>实现负责装配 {@link SiteStatus} 值对象（只含公开安全字段）。各登录方式开关应反映「该方式是否
     * 已配置且启用」，但<b>不</b>携带其凭据。</p>
     *
     * @return 公开站点状态聚合（不含任何敏感字段）
     */
    SiteStatus siteStatus();

    /**
     * 用户协议是否启用展示（F-4027）。
     *
     * @return 启用返回 {@code true}
     */
    boolean isUserAgreementEnabled();

    /**
     * 用户协议原文（未设置返回空串/null，门控由领域 {@code LegalDocument.publicContent} 应用）。
     *
     * @return 用户协议文本，可空
     */
    String userAgreementContent();

    /**
     * 隐私政策是否启用展示（F-4028）。
     *
     * @return 启用返回 {@code true}
     */
    boolean isPrivacyPolicyEnabled();

    /**
     * 隐私政策原文（未设置返回空串/null）。
     *
     * @return 隐私政策文本，可空
     */
    String privacyPolicyContent();
}
