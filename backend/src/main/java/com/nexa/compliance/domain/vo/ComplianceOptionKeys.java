package com.nexa.compliance.domain.vo;

/**
 * 合规子域使用的 Option 配置键常量（F-5017 留存开关/保留期、F-5021 同意条款版本）。
 *
 * <p>合规相关的可配置项经 {@code com.nexa.ops} 的 Option 机制持久化，键名集中在此声明，
 * 避免字符串散落。领域规则来源：API-ENDPOINTS §14.5 F-5017「留存开关与保留期可配」、
 * F-5021「同意闸门关联 §9.5 合规确认（payment_setting.compliance_*）」。</p>
 *
 * <p>注意：{@code payment_setting.compliance_*} 前缀的键被 {@code OptionRegistry} 守护为
 * 「禁经 PUT /api/option/ 修改」（须走 §9.5 专用确认端点），本类不重复定义这些键，只定义
 * 留存与隐私政策相关的可配置键。</p>
 */
public final class ComplianceOptionKeys {

    /** prompt/响应正文留存开关（F-5017，默认 false=不留存）。值为 {@code "true"}/{@code "false"}。 */
    public static final String PROMPT_RETENTION_ENABLED = "compliance.prompt_retention_enabled";

    /** prompt/响应正文保留天数（F-5017，默认 30，硬上限见 {@link PromptRetentionPolicy#MAX_RETENTION_DAYS}）。 */
    public static final String PROMPT_RETENTION_DAYS = "compliance.prompt_retention_days";

    /**
     * 当前生效的「含出境与留存条款」隐私政策/用户协议版本号（F-5021）。
     *
     * <p>同意闸门据此判定用户已接受的条款版本是否仍是最新（版本变更需重新同意）。</p>
     */
    public static final String CONSENT_TERMS_VERSION = "compliance.consent_terms_version";

    private ComplianceOptionKeys() {
    }
}
