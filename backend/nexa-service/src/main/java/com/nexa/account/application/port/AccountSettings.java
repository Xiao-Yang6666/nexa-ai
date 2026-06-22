package com.nexa.account.application.port;

/**
 * 账号系统设置端口（应用层依赖，基础设施/配置层实现）。
 *
 * <p>承载 PRD prd-account.md §15 系统开关与注册参数：注册总开关、新用户初始额度。
 * 定义为端口接口而非直接读 Spring 配置，是为了让用例可在单测中注入桩值
 * （如关闭注册、设定固定初始额度）而无需起 Spring 上下文。</p>
 */
public interface AccountSettings {

    /**
     * 是否允许注册。
     *
     * <p>PRD AC-1 前置「RegisterEnabled=true」；为 false 时注册用例直接拒绝（R1-否）。</p>
     *
     * @return 允许注册返回 {@code true}
     */
    boolean isRegisterEnabled();

    /**
     * 是否开启邮箱验证码校验。
     *
     * <p>PRD §15 系统开关 {@code EmailVerificationEnabled}；为 {@code true} 时注册必须携带有效
     * 验证码（PRD AC-1 R3「EmailVerificationEnabled 开启走验证码校验」、F-1005）。</p>
     *
     * @return 开启返回 {@code true}
     */
    boolean isEmailVerificationEnabled();

    /**
     * 新用户初始额度（QuotaForNewUser）。
     *
     * <p>PRD AC-1 R13：新用户 Quota=QuotaForNewUser。</p>
     *
     * @return 初始额度（整数额度单位）
     */
    long quotaForNewUser();
}
