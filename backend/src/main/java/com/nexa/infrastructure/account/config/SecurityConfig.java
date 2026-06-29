package com.nexa.infrastructure.account.config;

import com.nexa.infrastructure.relay.auth.RelayApiKeyAuthenticationFilter;
import com.nexa.shared.security.infrastructure.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Web 安全配置（基础设施层）。
 *
 * <p>本服务是无状态 JSON API：登录签发 JWT（{@code JwtTokenIssuer}），后续请求由
 * {@link JwtAuthenticationFilter} 从 {@code Authorization: Bearer} 或 {@code session} cookie 解析身份注入
 * {@code SecurityContext}（RBAC 三级鉴权身份层）。本配置负责：① 接入鉴权过滤器；② 声明公开放行面；
 * ③ 对受保护端点做<b>路径级粗粒度</b>角色门槛（与方法级 {@code @RequireRole} 注解互补）。</p>
 *
 * <p>路径级角色门槛（对齐 openapi securitySchemes 与 ROLE-PERMISSION-MATRIX §3，F-5031）：
 * <ul>
 *   <li>公开（{@code security: []}）：注册/登录/登出/找回/发码/OAuth 发起回调/passkey 登录入口——放行；</li>
 *   <li>管理端用户管理（adminAuth：{@code GET/POST/PUT /api/user/}、{@code POST /api/user/manage}）：要求 {@code ROLE_ADMIN}+；</li>
 *   <li>其余非公开端点：要求已认证（{@code authenticated()}）——细粒度级别由各 controller 的方法级
 *       {@code @RequireRole} 注解就近声明。</li>
 * </ul>
 * 角色层级：Spring 的 {@code hasRole("ADMIN")} 仅精确匹配 ROLE_ADMIN，故对「admin 或更高（root）」用
 * {@code hasAnyRole("ADMIN","ROOT")} 表达「≥ admin」语义（root&gt;admin，root 应能进 admin 端点）。</p>
 *
 * <p>关闭 CSRF / HTTP Basic / form login：无状态 token 鉴权模型，非浏览器表单提交，CSRF 令牌不适用。
 * 会话策略 STATELESS：不创建/不依赖 HttpSession，纯凭令牌。</p>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RelayApiKeyAuthenticationFilter relayApiKeyAuthenticationFilter;
    private final CorsProperties corsProperties;

    /**
     * @param jwtAuthenticationFilter         JWT 身份解析过滤器（com.nexa.shared.security 提供，作用 /api/**）
     * @param relayApiKeyAuthenticationFilter Relay API-Key 鉴权过滤器（com.nexa.relay 提供，仅作用 /v1/**）
     * @param corsProperties                  CORS 跨域白名单配置（app.cors.*）
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RelayApiKeyAuthenticationFilter relayApiKeyAuthenticationFilter,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.relayApiKeyAuthenticationFilter = relayApiKeyAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    /**
     * 定义安全过滤链：接入 JWT 过滤器 + 放行公开端点 + 管理端路径级角色门槛。
     *
     * @param http Spring Security HTTP 配置构建器
     * @return 构建后的过滤链
     * @throws Exception 配置构建异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 启用 CORS：使用下方 corsConfigurationSource bean。Spring Security 会在过滤链早期插入
                // CorsFilter，对跨域 OPTIONS 预检直接以 CORS 头响应并短路，无需进入鉴权逻辑。
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ===== CORS 预检：放行所有 OPTIONS 请求（浏览器预检不带凭证，须无条件通过）=====
                        // 与 .cors() 互补：即便 CorsFilter 已短路预检，此处显式 permitAll 兜底，确保
                        // 任何 OPTIONS 不被后续路径级角色门槛拦成 403。
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/user/register",   // F-1001 注册
                                "/api/user/login",       // F-1002 登录
                                "/api/user/login/2fa",   // F-1036 2FA 登录第二步（openapi security: []，登录链路第二步，凭 2FA 票据非 JWT）
                                "/api/user/logout",      // F-1003 登出（无状态，幂等回执）
                                "/api/user/reset",        // F-1007 提交重置新密码
                                "/api/verification",      // F-1004 发注册/找回验证码
                                "/api/reset_password",    // F-1006 发重置密码邮件
                                // ===== OAuth 发起/回调（openapi security: []，第三方授权链路，自证身份不带本站 JWT）=====
                                "/api/oauth/state",       // F-1015 生成 OAuth state（CSRF）暂存 aff（security: []）
                                "/api/oauth/discord",     // F-1018 Discord OAuth 登录/绑定回调（security: []）
                                "/api/oauth/linuxdo",     // F-1020 LinuxDO OAuth 登录/绑定回调（security: []）
                                "/api/oauth/wechat",      // F-1021 微信扫码发起（security: []）
                                "/api/oauth/wechat/bind", // F-1022 微信绑定/登录（security: []，登录/注册分支）
                                "/api/oauth/telegram/login", // F-1051 Telegram 登录（security: []，HMAC 校验自证身份）
                                "/api/oauth/*",           // F-1016 通用 OAuth 回调 GET /api/oauth/{provider}（security: []，单段通配，
                                                          // 覆盖 github/oidc/自定义；上面固定段先于通配匹配，telegram 子路径用 /** 另放行）
                                "/api/oauth/**",          // F-1051/F-1016 兜底 OAuth 多段子路径（如 /api/oauth/telegram/login），均为 security: [] 链路
                                // ===== 支付回调（openapi security: []，第三方支付网关回调，无本站凭证，凭签名/幂等键自证）=====
                                "/api/topup/callback/*",  // F-2044 支付回调入账 POST /api/topup/callback/{provider}（security: []）
                                // ===== 公开站点只读端点（openapi security: []）=====
                                "/api/pricing",           // F-2048 公开模型价格页（security: []，PublicView 零泄露，未登录可见）
                                "/api/status",            // F-4039 营销首页公开状态聚合
                                "/api/status-page/*",     // F-4026 Uptime-Kuma 状态页代理 GET /api/status-page/{slug}（security: []）
                                "/heartbeat/*",           // F-4026 Uptime-Kuma 心跳数据代理 GET /heartbeat/{slug}（security: []）
                                "/api/user_agreement",    // F-4027 用户协议公开内容
                                "/api/privacy_policy",    // F-4028 隐私政策公开内容
                                "/api/user/passkey/login/begin",  // F-1029 passkey 登录发起（security: []）
                                "/api/user/passkey/login/finish"  // F-1029 passkey 登录完成（security: []，登录入口）
                        ).permitAll()
                        // ===== USER self-scope 子路径：先于下面宽泛的 admin 门槛显式放到 authenticated() =====
                        // 【为什么必须放在 admin 规则之前】Spring Security matcher 顺序敏感，且路径级 admin 门槛若用
                        // 宽泛前缀会吞掉这些 USER 级子路径。这些端点本人 self-scope，由各 controller 方法级
                        // @RequireRole(USER) 就近控制权限（CheckinController/UserController 等），路径级只需「已认证」。
                        // 原 bug：admin 门槛误把 /api/user/checkin 等子路径要求成 ADMIN → 普通用户 403。
                        .requestMatchers(HttpMethod.GET, "/api/user/checkin").authenticated()   // F-1047 签到状态查询（USER）
                        .requestMatchers(HttpMethod.POST, "/api/user/checkin").authenticated()  // F-1046 每日签到（USER）
                        .requestMatchers("/api/user/self", "/api/user/self/**").authenticated() // F-1xxx 本人 self-scope（USER）
                        .requestMatchers(HttpMethod.POST, "/api/user/topup").authenticated()    // F-2044 卡密兑换入账（USER）——RedeemController @RequireRole(USER) 就近控权；
                        // 原 bug：下面宽泛的 /api/user/* admin 门槛误把 topup 要求成 ADMIN → 普通用户兑换 403（与 checkin 同类坑）。
                        // ===== 管理端用户管理（adminAuth，要求 ≥ admin；root 亦可）=====
                        // 精确匹配真正的管理端用户管理端点，不用宽泛前缀，避免误伤上面的 USER 子路径：
                        //   - GET  /api/user/        列表（F-1008）       - POST /api/user/        创建（F-1009）
                        //   - PUT  /api/user/        更新资料（F-1011）    - GET  /api/user/search  搜索（F-1008）
                        //   - POST /api/user/manage  状态管理（F-1010）    - /api/user/{id}/**      他人管理操作（详情/绑定/重置）
                        // 注：/api/user/{id} 形如纯数字段，用 /api/user/* 兜 {id}；上面已显式排除 checkin/self 子路径，
                        // 故此处 /api/user/* 只会落到管理端 {id} 路径（self/checkin 已先匹配走 authenticated）。
                        .requestMatchers(HttpMethod.GET, "/api/user/").hasAnyRole("ADMIN", "ROOT")       // F-1008 列表
                        // 无尾斜杠形态 GET /api/user（调用方常这样发）也须门槛 ADMIN+：handler 已同时映射 ""/"/"，
                        // 这里补无尾斜杠的路径级规则，确保普通用户两种写法都进不来（鉴权不削弱）。
                        .requestMatchers(HttpMethod.GET, "/api/user").hasAnyRole("ADMIN", "ROOT")        // F-1008 列表（无尾斜杠）
                        .requestMatchers(HttpMethod.POST, "/api/user/").hasAnyRole("ADMIN", "ROOT")      // F-1009 创建
                        .requestMatchers(HttpMethod.PUT, "/api/user/").hasAnyRole("ADMIN", "ROOT")       // F-1011 更新资料
                        .requestMatchers(HttpMethod.GET, "/api/user/search").hasAnyRole("ADMIN", "ROOT") // F-1008 搜索
                        .requestMatchers(HttpMethod.POST, "/api/user/manage").hasAnyRole("ADMIN", "ROOT")// F-1010 状态管理
                        .requestMatchers("/api/user/*", "/api/user/*/**").hasAnyRole("ADMIN", "ROOT")    // F-1010/1012 /api/user/{id}/** 他人管理
                        // ===== Relay 中继端点（/v1/**）：API-Key 鉴权（非 JWT），由 RelayApiKeyAuthenticationFilter
                        // 反查 tokens 表注入 RelayApiKeyAuthentication（已认证 COMMON）；此处路径级只需「已认证」，
                        // 细粒度 USER 门槛由各 relay handler 的方法级 @RequireRole(USER) 就近声明。=====
                        .requestMatchers("/v1/**").authenticated()
                        // ===== 其余非公开端点：需认证（细粒度级别由方法级 @RequireRole 注解声明）=====
                        .anyRequest().authenticated())
                // /v1/** 走 API-Key 鉴权，须在 JWT 过滤器之前接入：认证成功后置 RelayApiKeyAuthentication，
                // JWT 过滤器见上下文已有认证即跳过；/api/** 因本过滤器 shouldNotFilter 跳过，仍走 JWT。
                .addFilterBefore(relayApiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // JWT 身份解析显式接在 relay 过滤器之后（保证 /v1 先经 API-Key：否则 sk- key 会被 JWT 当令牌
                // 解析失败误判 401）；用户名密码过滤器本身已禁用，仅作链路定位锚点。
                .addFilterAfter(jwtAuthenticationFilter, RelayApiKeyAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS 配置源：声明允许的前端 origin / 方法 / 请求头 + 允许携带凭证。
     *
     * <p>allowedOrigins 从 {@link CorsProperties}（app.cors.allowed-origins）读取，联调期含
     * 由环境变量 APP_CORS_ALLOWED_ORIGINS 配置，生产改配置即可。
     * 因 allowCredentials=true（前端带 JWT/session cookie），CORS 规范禁止 origin 用通配 {@code *}，
     * 故此处列具体 origin 白名单。allowedMethods 含业务用到的 GET/POST/PUT/DELETE 及预检 OPTIONS；
     * allowedHeaders 放行 Authorization（JWT）/ Content-Type 等；exposedHeaders 暴露 Authorization
     * 以便前端读取刷新后的令牌。maxAge 缓存预检结果 1 小时，减少重复 OPTIONS。</p>
     *
     * @return 注册到 {@code /**} 全路径的 CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        // 前端带 JWT/session cookie，须允许凭证；与通配 origin 互斥，故上面用具体 origin 白名单。
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
