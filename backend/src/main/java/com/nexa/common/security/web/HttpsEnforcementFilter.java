package com.nexa.common.security.web;

import com.nexa.common.security.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全站 HTTPS 强制过滤器（基础设施层，横切中间件）。
 *
 * <p>实现「全站 HTTPS 强制」：生产服务在 {@code https://nexa.ai}（见 openapi servers），TLS 由前置
 * 反向代理（nginx/caddy）终止后以明文转发给应用，应用据 {@code X-Forwarded-Proto}（需开启
 * {@code server.forward-headers-strategy=framework}，由 Spring 归一到 {@link HttpServletRequest#isSecure()}）
 * 判定原始协议是否为 HTTPS。</p>
 *
 * <p>命中明文 HTTP 时按配置策略处理：
 * <ul>
 *   <li>{@code redirect}（默认）：对幂等的 GET/HEAD 用 301、其余用 308（保留方法与请求体）跳转到同名
 *       https URL，避免 POST 被降级成 GET 丢请求体；</li>
 *   <li>{@code reject}：直接 403 拒绝（适合纯 API、不希望自动跳转的场景）。</li>
 * </ul>
 * 走 HTTPS 的请求则按配置追加 HSTS 响应头（{@code Strict-Transport-Security}），指示浏览器后续强制
 * 走 HTTPS，缓解 SSL 剥离攻击。本过滤器在 {@code security.https.enabled=false}（本地默认）时整体放行不干预。</p>
 *
 * <p>设计依据：backend-engineer §3.4 安全默认；NFR 安全。排在最前（{@link Ordered#HIGHEST_PRECEDENCE}）
 * 以在进入业务/安全链前先完成协议矫正。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpsEnforcementFilter extends OncePerRequestFilter {

    private static final String HSTS_HEADER = "Strict-Transport-Security";

    private final SecurityProperties.Https config;

    /**
     * @param properties 安全配置（读取 HTTPS 强制子段）
     */
    public HttpsEnforcementFilter(SecurityProperties properties) {
        this.config = properties.getHttps();
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 未启用强制（本地开发）→ 完全不干预，放行。
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // request.isSecure() 已综合 forward-headers（X-Forwarded-Proto）归一判定原始协议。
        if (request.isSecure()) {
            applyHsts(response);
            filterChain.doFilter(request, response);
            return;
        }

        // 明文 HTTP：按策略处理。
        if ("reject".equalsIgnoreCase(config.getOnInsecure())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "HTTPS is required");
            return;
        }
        redirectToHttps(request, response);
    }

    /**
     * 把当前明文请求重定向到等价的 https URL。
     *
     * <p>GET/HEAD 用 301（永久），其余方法用 308（永久 + 保留方法与请求体），避免 POST/PUT 被
     * 浏览器降级成 GET 丢失请求体。</p>
     */
    private void redirectToHttps(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        StringBuilder target = new StringBuilder("https://");
        target.append(request.getServerName());
        target.append(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            target.append('?').append(query);
        }

        String method = request.getMethod();
        int status = ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                ? HttpServletResponse.SC_MOVED_PERMANENTLY      // 301
                : 308;                                          // Permanent Redirect（保留方法/体）
        response.setStatus(status);
        response.setHeader("Location", target.toString());
    }

    /** 对 HTTPS 请求按配置追加 HSTS 响应头。 */
    private void applyHsts(HttpServletResponse response) {
        if (!config.isHstsEnabled()) {
            return;
        }
        StringBuilder hsts = new StringBuilder("max-age=").append(config.getHstsMaxAgeSeconds());
        if (config.isHstsIncludeSubDomains()) {
            hsts.append("; includeSubDomains");
        }
        response.setHeader(HSTS_HEADER, hsts.toString());
    }
}
