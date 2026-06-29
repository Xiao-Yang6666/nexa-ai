package com.nexa.infrastructure.deployment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * io.net 企业集成设置（基础设施层，对齐 Option {@code model_deployment.ionet.*}）。
 *
 * <p>从 {@code deployment.ionet.*} 前缀读取 io.net 企业 API 的接入配置（base-url / api-key / 启用开关）。
 * 对应 DATA-MODEL Option 的 {@code ionet.enabled}/{@code ionet.api_key}/{@code ionet.base_url}
 * （API-ENDPOINTS §10）。后续接入 §15 动态系统设置（DB OptionMap）时仅换数据来源、不动签名。</p>
 *
 * <p><b>安全声明</b>：{@code apiKey} 为敏感凭证，仅在 infra 层用于上游鉴权，<b>绝不</b>下发到任何
 * 客户端响应（敏感键剔除见 {@code IonetClientImpl}）。配置缺省为空，缺省时连接类调用按
 * 「api_key is required / 未配置」语义失败，不在启动期报错。</p>
 */
@Component
@ConfigurationProperties(prefix = "deployment.ionet")
public class IonetSettings {

    /** io.net 企业 API 基址（如 https://api.io.net），默认占位空（缺省视为未配置）。 */
    private String baseUrl = "";

    /** io.net 企业 API Key（敏感凭证，绝不下发响应），默认空。 */
    private String apiKey = "";

    /** io.net 集成总开关（对齐 OptionMap[ionet.enabled]），默认关闭。 */
    private boolean enabled = false;

    /** @return io.net API 基址 */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** @param baseUrl io.net API 基址（配置绑定注入） */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** @return io.net API Key（敏感，仅 infra 内部使用） */
    public String getApiKey() {
        return apiKey;
    }

    /** @param apiKey io.net API Key（配置绑定注入） */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** @return 集成是否启用 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 集成总开关（配置绑定注入） */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否已配置 api_key（非空白）。
     *
     * @return 已配置返回 true（对齐契约 {@code configured: <api_key 非空>}）
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
