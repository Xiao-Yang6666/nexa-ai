package com.nexa.relay.infrastructure.auth;

import com.nexa.shared.security.domain.rbac.ActorRole;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.vo.TokenStatus;
import com.nexa.token.domain.repository.TokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Relay API-Key 鉴权过滤器（基础设施层 / Web 适配，REQ-API-KEY-AUTH）。
 *
 * <p>命脉最后一环：{@code /v1/**} 中继端点的客户用 {@code tokens.key}（形如 {@code sk-...}）鉴权，
 * <b>不是</b>登录签发的 JWT。本过滤器只作用于 {@code /v1/**}（{@link #shouldNotFilter} 对非 /v1 返 true 跳过，
 * 不触碰 {@code /api/**} 的 JWT 链路）：从 {@code Authorization: Bearer ***（OpenAI 风格，优先）
 * 或 {@code x-api-key: ***（Anthropic 官方 SDK 风格，回退）取明文 key →
 * {@link TokenRepository#findByKey} 反查 token → 校验启用/未过期/额度 → 构造携带真实
 * {@code userId/group/tokenId/tokenName} 的 {@link RelayApiKeyAuthentication} 注入 {@code SecurityContext}。</p>
 *
 * <p>校验规则（按 DB-SCHEMA §2 tokens 语义）：
 * <ul>
 *   <li>token 存在（findByKey 命中，软删已由 {@code @SQLRestriction} 过滤）；</li>
 *   <li>{@code status == ENABLED}（禁用/派生禁用态拒绝）；</li>
 *   <li>未过期：{@code expired_time == -1}（永不过期）或 {@code expired_time > now}（epoch 秒）；</li>
 *   <li>额度：{@code unlimited_quota == true} 或 {@code remain_quota > 0}。</li>
 * </ul>
 * 任一不满足或凭据缺失/为空 → 就地 401，message 稳定通用（{@code invalid api key}），不回显 token 细节、
 * 不区分「不存在/禁用/过期/耗尽」（避免给攻击者探测反馈，安全默认，不吞错）。</p>
 *
 * <p>链路位置：在 {@code JwtAuthenticationFilter} 之前接入。/v1 经本过滤器认证成功后置入
 * {@link RelayApiKeyAuthentication}，JWT 过滤器见上下文已有认证即跳过（不覆盖），互不干扰；/api 因本过滤器
 * {@code shouldNotFilter} 直接跳过，仍走原 JWT 鉴权。</p>
 */
@Component
public class RelayApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** Bearer 前缀（OpenAI 风格 {@code Authorization: Bearer sk-...}）。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Anthropic 官方 SDK 风格的 API-Key 请求头（{@code x-api-key: sk-...}）。 */
    private static final String X_API_KEY_HEADER = "x-api-key";

    /** 本过滤器作用的 relay 路径前缀（仅 /v1/** 走 API-Key 鉴权）。 */
    private static final String RELAY_PATH_PREFIX = "/v1/";

    /** 稳定通用的鉴权失败提示（不区分失败细分原因，不泄露 token 细节）。 */
    private static final String INVALID_API_KEY = "invalid api key";

    private final TokenRepository tokenRepository;

    /**
     * @param tokenRepository 令牌仓储（按 key 反查 + 校验归属/有效性）
     */
    public RelayApiKeyAuthenticationFilter(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅对 {@code /v1/**} 生效；其余路径（含 {@code /api/**}）跳过，交回 JWT 链路。
     * CORS 预检 OPTIONS 也跳过（无凭据，由 CorsFilter/SecurityConfig 放行）。</p>
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return path == null || !path.startsWith(RELAY_PATH_PREFIX);
    }

    /**
     * {@inheritDoc}
     *
     * <p>提取 key → 反查校验 → 注入 {@code SecurityContext}；缺失/非法就地 401，不放行穿透。</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 已有认证（理论上 /v1 本过滤器最先跑，留作幂等保护）则不覆盖。
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = extractApiKey(request);
        if (key == null) {
            // /v1 端点强制 API-Key：缺失凭据即就地 401（不放行让后续 RBAC 误判为 JWT 缺失）。
            writeUnauthorized(response);
            return;
        }

        Optional<Token> maybeToken = tokenRepository.findByKey(key);
        if (maybeToken.isEmpty() || !isUsable(maybeToken.get())) {
            writeUnauthorized(response);
            return;
        }

        Token token = maybeToken.get();
        AuthenticatedActor actor = new AuthenticatedActor(token.userId(), token.name(), ActorRole.COMMON);
        RelayApiKeyAuthentication authentication = new RelayApiKeyAuthentication(
                actor, token.id(), token.group(), token.name());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * token 是否可用于鉴权放行：启用 + 未过期 + 有额度。
     *
     * @param token findByKey 命中的 token 聚合
     * @return 三项全满足返回 true
     */
    private boolean isUsable(Token token) {
        if (token.status() != TokenStatus.ENABLED) {
            return false;
        }
        long expiredTime = token.expiredTime();
        if (expiredTime != Token.NEVER_EXPIRE && expiredTime <= Instant.now().getEpochSecond()) {
            return false;
        }
        return token.unlimitedQuota() || token.remainQuota() > 0;
    }

    /**
     * 从 {@code Authorization: Bearer <key>} 提取明文 key。
     *
     * @param request HTTP 请求
     * @return 非空 key；缺失/空返回 null
     */
    private String extractApiKey(HttpServletRequest request) {
        // 优先 OpenAI 风格：Authorization: Bearer sk-...
        String authz = request.getHeader("Authorization");
        if (authz != null && authz.startsWith(BEARER_PREFIX)) {
            String key = authz.substring(BEARER_PREFIX.length()).trim();
            if (!key.isEmpty()) {
                return key;
            }
        }
        // 回退 Anthropic 官方 SDK 风格：x-api-key: sk-...
        String xApiKey = request.getHeader(X_API_KEY_HEADER);
        if (xApiKey != null) {
            String key = xApiKey.trim();
            if (!key.isEmpty()) {
                return key;
            }
        }
        return null;
    }

    /**
     * 就地写 401 错误信封（与全站 {@code ApiResponse} {success:false,message,data} 结构一致）。
     *
     * <p>过滤器在 MVC 消息转换器之前，手写最小 JSON；清上下文不让损坏凭据穿透。</p>
     *
     * @param response HTTP 响应
     * @throws IOException 写出失败
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + INVALID_API_KEY + "\",\"data\":null}");
    }
}
