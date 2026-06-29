package com.nexa.infrastructure.account.config;

import com.nexa.application.account.port.AccountSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 账号系统设置端口 {@link AccountSettings} 的配置驱动实现（基础设施层）。
 *
 * <p>从 {@code application.yml} 的 {@code account.*} 前缀读取注册开关与新用户初始额度
 * （PRD §15 系统开关 / AC-1 R13）。应用层只依赖 {@link AccountSettings} 端口，单测注入桩，
 * 这里把"配置来源"这一基础设施关注点收敛到 infra 层（DDD §2.3）。</p>
 *
 * <p>用 {@code @ConfigurationProperties} 绑定，字段可热配（后续接 §15 动态系统设置时改为 DB 读取，
 * 仅换实现不动应用层签名）。</p>
 */
@Component
@ConfigurationProperties(prefix = "account")
public class ConfigAccountSettings implements AccountSettings {

    /** 是否开放注册（PRD AC-1 前置 RegisterEnabled），默认开放。 */
    private boolean registerEnabled = true;

    /** 是否开启邮箱验证码校验（PRD §15 EmailVerificationEnabled / F-1005），默认关闭。 */
    private boolean emailVerificationEnabled = false;

    /** 新用户初始额度（PRD AC-1 R13 QuotaForNewUser），默认 0。 */
    private long quotaForNewUser = 0L;

    /** {@inheritDoc} */
    @Override
    public boolean isRegisterEnabled() {
        return registerEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmailVerificationEnabled() {
        return emailVerificationEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public long quotaForNewUser() {
        return quotaForNewUser;
    }

    /** @param registerEnabled 注册总开关（由配置绑定注入） */
    public void setRegisterEnabled(boolean registerEnabled) {
        this.registerEnabled = registerEnabled;
    }

    /** @param emailVerificationEnabled 邮箱验证码校验开关（由配置绑定注入） */
    public void setEmailVerificationEnabled(boolean emailVerificationEnabled) {
        this.emailVerificationEnabled = emailVerificationEnabled;
    }

    /** @param quotaForNewUser 新用户初始额度（由配置绑定注入） */
    public void setQuotaForNewUser(long quotaForNewUser) {
        this.quotaForNewUser = quotaForNewUser;
    }
}
