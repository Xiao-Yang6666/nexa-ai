package com.nexa.shared.security.infrastructure.auth;

import com.nexa.shared.security.application.port.TokenPrincipalResolver;
import com.nexa.shared.security.domain.exception.AuthenticationRequiredException;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT 鉴权过滤器（基础设施层 / Web 适配）——三级鉴权的统一身份解析入口。
 *
 * <p>职责：在 Spring Security 过滤链中，对每个请求<b>解析操作者身份并注入 {@code SecurityContext}</b>。
 * 凭据来源（按优先级）：
 * <ol>
 *   <li>{@code Authorization: Bearer <jwt>}（登录签发的 access token，无状态）；</li>
 *   <li>{@code session} cookie（对齐 openapi sessionAuth/adminAuth/rootAuth 的 cookie 会话凭据，
 *       本切片同样承载 JWT 紧凑串，统一解析）。</li>
 * </ol>
 * 解析成功 → 放入 {@link ActorAuthenticationToken}（authenticated），后续路径鉴权与方法级权限注解据此判定。</p>
 *
 * <p>三级语义落地说明：本过滤器只负责<b>「你是谁 + 你什么角色」</b>（身份注入）。粗粒度的
 * UserAuth/AdminAuth/RootAuth <b>级别门槛</b>由两道协同实现：① {@code SecurityConfig} 按路径前缀
 * （/api/user/** vs 管理端/root 端点）配置最低 {@code hasRole}；② 方法级 {@code @RequireRole(level)}
 * 注解在 controller 方法上做就近、细粒度判定（见 interfaces 层注解 + 拦截器）。身份与授权分离，符合
 * 单一职责（backend-engineer §3.4）。</p>
 *
 * <p>错误处理：凭据存在但非法（验签失败/过期/声明坏）由 {@link TokenPrincipalResolver} 抛
 * {@link AuthenticationRequiredException}，本过滤器就地写 401 JSON（对齐 openapi {@code UnauthorizedError}），
 * <b>不</b>让损坏令牌继续穿过链路（安全默认，不吞错）。凭据缺失则保持匿名继续——是否拦截交由后续授权层。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 会话凭据 cookie 名（对齐 openapi securitySchemes 的 {@code name: session}）。 */
    private static final String SESSION_COOKIE = "session";

    private final TokenPrincipalResolver principalResolver;

    /**
     * @param principalResolver 令牌主体解析端口（基础设施注入 JWT 实现）
     */
    public JwtAuthenticationFilter(TokenPrincipalResolver principalResolver) {
        this.principalResolver = principalResolver;
    }

    /**
     * {@inheritDoc}
     *
     * <p>解析凭据→注入 SecurityContext→放行；非法凭据就地 401。</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String credential = extractCredential(request);

        // 已有认证（如被其他机制设置）则不覆盖；仅在空位上尝试注入，避免重复解析。
        if (credential != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Optional<AuthenticatedActor> actor = principalResolver.resolve(credential);
                actor.ifPresent(a -> {
                    ActorAuthenticationToken token = new ActorAuthenticationToken(a);
                    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(token);
                });
            } catch (AuthenticationRequiredException e) {
                // 损坏/伪造令牌：清上下文并就地 401，不继续穿透链路。
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, e.getMessage());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求提取原始凭据：优先 Authorization Bearer，回退 session cookie。
     *
     * @param request HTTP 请求
     * @return 凭据紧凑串；均无则 null
     */
    private String extractCredential(HttpServletRequest request) {
        String authz = request.getHeader("Authorization");
        if (authz != null && authz.startsWith(BEARER_PREFIX)) {
            String token = authz.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (SESSION_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 就地写 401 错误信封（对齐全站 {@code ApiResponse} 的 {@code {success:false,message}} 结构）。
     *
     * <p>不引入 ApiResponse 类型依赖（过滤器在 Spring MVC 消息转换器之前），手写最小 JSON；
     * message 用稳定通用提示，不回显令牌细节。</p>
     *
     * @param response HTTP 响应
     * @param message  稳定提示
     * @throws IOException 写出失败
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String safe = message == null ? "authentication required" : message.replace("\"", "'");
        response.getWriter().write("{\"success\":false,\"message\":\"" + safe + "\",\"data\":null}");
    }
}
