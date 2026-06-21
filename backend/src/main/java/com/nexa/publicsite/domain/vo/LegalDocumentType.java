package com.nexa.publicsite.domain.vo;

/**
 * 法律文本类型（值对象 / 枚举，F-4027/F-4028）。
 *
 * <p>收纳公开站点可读的法律文本：用户协议、隐私政策。各自有「内容 + 是否启用」两项配置
 * （对齐 openapi {@code GET /api/user_agreement}、{@code GET /api/privacy_policy}）。
 * 用枚举区分类型，便于 {@link LegalDocument} 与设置端口按类型取值，避免重复端点逻辑。</p>
 */
public enum LegalDocumentType {

    /** 用户协议（F-4027，openapi UserAgreement）。 */
    USER_AGREEMENT,

    /** 隐私政策（F-4028，openapi PrivacyPolicy）。 */
    PRIVACY_POLICY
}
