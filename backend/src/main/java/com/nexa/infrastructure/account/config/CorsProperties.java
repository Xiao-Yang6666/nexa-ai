package com.nexa.infrastructure.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS 跨域配置属性（基础设施层）。
 *
 * <p>从 {@code application.yml} 的 {@code app.cors.*} 前缀读取允许的前端来源。前后端分离部署下
 * 前端与后端不同源，浏览器对 {@code /api/*} 跨域请求会先发 OPTIONS 预检；此处声明允许的 origin 白名单
 * 供 {@code CorsConfigurationSource} 使用。</p>
 *
 * <p>因 {@code allowCredentials=true}（前端携带 JWT / session cookie），CORS 规范禁止 origin 用通配 {@code *}，
 * 必须列出具体 origin。具体 origin 由环境变量 APP_CORS_ALLOWED_ORIGINS 配置，
 * 生产通过环境变量 {@code APP_CORS_ALLOWED_ORIGINS}（逗号分隔）覆盖为真实前端域名即可，不改代码。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** 允许跨域访问的前端 origin 白名单（精确匹配，由配置绑定注入）。 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** @return 允许跨域访问的前端 origin 白名单 */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /** @param allowedOrigins 允许跨域访问的前端 origin 白名单（由配置绑定注入） */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
