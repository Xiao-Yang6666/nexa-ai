package com.nexa.telegram.infrastructure.config;

import com.nexa.telegram.application.port.TelegramSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Telegram 登录设置端口 {@link TelegramSettings} 的配置驱动实现（基础设施层）。
 *
 * <p>从 {@code application.yml} 的 {@code telegram.*} 前缀读取登录开关、Bot Token、授权时效窗口。
 * 应用层只依赖 {@link TelegramSettings} 端口，单测注入桩；这里把「配置来源」这一基础设施关注点
 * 收敛到 infra 层（DDD §2.3）。后续接 §15 动态系统设置时改为 DB 读取，仅换实现不动应用层签名。</p>
 *
 * <p><b>安全</b>：{@code botToken} 是机密，仅服务端用于派生 HMAC 密钥；本类不提供任何把 token 暴露到
 * 客户视图/日志的路径（{@code StatusAggregateView} 只读 {@link #isTelegramLoginEnabled()} 布尔）。
 * 默认值刻意为「关闭 + 空 token + 86400 秒窗口」——未显式配置时 Telegram 登录默认不可用，避免空 token 误放行。</p>
 */
@Component
@ConfigurationProperties(prefix = "telegram")
public class ConfigTelegramSettings implements TelegramSettings {

    /** 是否启用 Telegram 登录（默认关闭：未配置 token 时不应可用）。 */
    private boolean loginEnabled = false;

    /** Telegram Bot Token（HMAC 密钥来源，机密，默认空）。 */
    private String botToken = "";

    /** 授权数据有效期窗口秒（默认 86400=1 天，Telegram 官方建议范围内；<=0 表示不校验时效）。 */
    private long authValiditySeconds = 86400L;

    /** {@inheritDoc} */
    @Override
    public boolean isTelegramLoginEnabled() {
        return loginEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public String botToken() {
        return botToken;
    }

    /** {@inheritDoc} */
    @Override
    public long authValiditySeconds() {
        return authValiditySeconds;
    }

    /** @param loginEnabled Telegram 登录开关（由配置绑定注入） */
    public void setLoginEnabled(boolean loginEnabled) {
        this.loginEnabled = loginEnabled;
    }

    /** @param botToken Bot Token（由配置绑定注入；机密，勿入日志） */
    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    /** @param authValiditySeconds 授权时效窗口秒（由配置绑定注入） */
    public void setAuthValiditySeconds(long authValiditySeconds) {
        this.authValiditySeconds = authValiditySeconds;
    }
}
