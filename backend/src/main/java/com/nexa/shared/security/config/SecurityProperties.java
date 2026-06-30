package com.nexa.shared.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全横切配置属性（基础设施层，绑定 {@code security.*} 前缀）。
 *
 * <p>把 HTTPS 强制策略、HSTS、敏感字段加密密钥等基础设施关注点收敛到 infra 层（DDD §2.3），
 * 经 {@code @ConfigurationProperties} 从 {@code application.yml} 注入，密钥等敏感项<b>用环境变量覆盖</b>
 * 不硬编码（backend-engineer §3.4 配置外置）。</p>
 */
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** HTTPS 强制相关配置。 */
    private final Https https = new Https();

    /** 敏感字段加密相关配置。 */
    private final Encryption encryption = new Encryption();

    /** @return HTTPS 配置子段 */
    public Https getHttps() {
        return https;
    }

    /** @return 加密配置子段 */
    public Encryption getEncryption() {
        return encryption;
    }

    /**
     * 全站 HTTPS 强制配置。
     *
     * <p>生产部署在 TLS 终止反向代理（nginx/caddy）之后，应用通过 {@code X-Forwarded-Proto} 感知
     * 原始协议（需 {@code server.forward-headers-strategy=framework}）。本段控制：是否启用强制、
     * 明文请求是重定向还是拒绝、以及 HSTS 头参数。</p>
     */
    public static class Https {

        /** 是否启用 HTTPS 强制（本地开发可关；生产开）。默认关，避免本地 http 调试被重定向打断。 */
        private boolean enabled = false;

        /** 明文 HTTP 命中时的处理策略：{@code redirect}（308 跳 https）或 {@code reject}（拒绝）。 */
        private String onInsecure = "redirect";

        /** 是否下发 HSTS 响应头（Strict-Transport-Security），仅在 enabled 且走 https 时有意义。 */
        private boolean hstsEnabled = true;

        /** HSTS max-age 秒数（默认 1 年）。 */
        private long hstsMaxAgeSeconds = 31_536_000L;

        /** HSTS 是否含 includeSubDomains。 */
        private boolean hstsIncludeSubDomains = true;

        /** @return 是否启用 HTTPS 强制 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用 HTTPS 强制 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return 明文请求处理策略（redirect|reject） */
        public String getOnInsecure() {
            return onInsecure;
        }

        /** @param onInsecure 明文请求处理策略 */
        public void setOnInsecure(String onInsecure) {
            this.onInsecure = onInsecure;
        }

        /** @return 是否下发 HSTS 头 */
        public boolean isHstsEnabled() {
            return hstsEnabled;
        }

        /** @param hstsEnabled 是否下发 HSTS 头 */
        public void setHstsEnabled(boolean hstsEnabled) {
            this.hstsEnabled = hstsEnabled;
        }

        /** @return HSTS max-age 秒数 */
        public long getHstsMaxAgeSeconds() {
            return hstsMaxAgeSeconds;
        }

        /** @param hstsMaxAgeSeconds HSTS max-age 秒数 */
        public void setHstsMaxAgeSeconds(long hstsMaxAgeSeconds) {
            this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
        }

        /** @return HSTS 是否含 includeSubDomains */
        public boolean isHstsIncludeSubDomains() {
            return hstsIncludeSubDomains;
        }

        /** @param hstsIncludeSubDomains HSTS 是否含 includeSubDomains */
        public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
            this.hstsIncludeSubDomains = hstsIncludeSubDomains;
        }
    }

    /**
     * 敏感字段加密配置。
     *
     * <p>对称密钥以 Base64 编码注入（AES-256 需 32 字节 → Base64 44 字符）。
     * <b>生产必须用环境变量覆盖</b>本地默认值，密钥绝不入库/入日志。</p>
     */
    public static class Encryption {

        /**
         * AES-256-GCM 主密钥（Base64 编码的 32 字节）。本地默认值仅供开发/编译期，
         * 生产用 {@code SECURITY_ENCRYPTION_KEY} 环境变量覆盖。
         */
        private String key = "";

        /** @return Base64 编码的对称主密钥 */
        public String getKey() {
            return key;
        }

        /** @param key Base64 编码的对称主密钥 */
        public void setKey(String key) {
            this.key = key;
        }
    }
}
