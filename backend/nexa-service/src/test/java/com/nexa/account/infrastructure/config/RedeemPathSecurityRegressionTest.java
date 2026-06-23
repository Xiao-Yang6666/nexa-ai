package com.nexa.account.infrastructure.config;

import com.nexa.billing.application.GenerateRedemptionsUseCase;
import com.nexa.billing.application.ListRedemptionsUseCase;
import com.nexa.billing.application.RedeemCodeUseCase;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.billing.interfaces.api.BillingExceptionHandler;
import com.nexa.billing.interfaces.api.RedeemController;
import com.nexa.billing.interfaces.api.RedemptionController;
import com.nexa.growth.application.DailyCheckinUseCase;
import com.nexa.growth.application.QueryCheckinStatusUseCase;
import com.nexa.growth.domain.vo.CheckinStats;
import com.nexa.growth.interfaces.api.CheckinController;
import com.nexa.relay.infrastructure.auth.RelayApiKeyAuthenticationFilter;
import com.nexa.shared.security.domain.rbac.ActorRole;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.infrastructure.auth.ActorAuthenticationToken;
import com.nexa.shared.security.infrastructure.auth.JwtAuthenticationFilter;
import com.nexa.shared.security.interfaces.api.SecurityExceptionHandler;
import com.nexa.shared.security.interfaces.web.RequireRoleInterceptor;
import com.nexa.shared.security.interfaces.web.SecurityWebMvcConfig;
import com.nexa.token.domain.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig 路径级鉴权回归（R5-redeem 加固，守护 commit 0cbe497 修复的 topup 403 bug）。
 *
 * <p><b>为什么要起真实 {@code SecurityFilterChain}</b>：本 bug 不在方法级 {@code @RequireRole}
 * （{@code standaloneSetup} 能覆盖的层），而在 {@link SecurityConfig} 的<b>路径级 matcher 顺序</b>——
 * 宽泛的 {@code /api/user/*} ADMIN 门槛若排在 {@code /api/user/topup} 之前，会把普通用户卡密兑换吞成 403。
 * {@code standaloneSetup} 不装载 Spring Security 过滤链，<b>无法</b>守护此类回归，故此处经真实
 * {@link SecurityConfig#filterChain} 全量 {@code authorizeHttpRequests} 规则断言。</p>
 *
 * <p><b>为什么不用 {@code @WebMvcTest}</b>：{@code @WebMvcTest} 会引导整个 {@code NexaApplication}
 * 组件扫描，连带拉入 HttpsEnforcementFilter（依赖 SecurityProperties）与全模块 25 个 ControllerAdvice
 * 及其用例依赖——上下文极脆。改用自包含最小 Web 上下文：只 {@link Import} 真实 {@link SecurityConfig}
 * 与 {@link SecurityWebMvcConfig}，用桩装配过滤器的叶子依赖（JWT 解析器恒空、relay 仓储 mock），
 * 既跑真链路又不连 DB / 不引全量 Bean。</p>
 *
 * <p>身份用 spring-security-test 的 {@code authentication()} 后置器直接注入 {@code SecurityContext}
 * （等价 JWT 过滤器解析成功后的状态）；过滤链据其 {@code ROLE_*} 权限做路径级判定。控制器用例 mock。</p>
 */
@SpringJUnitConfig
@WebAppConfiguration
@DisplayName("SecurityConfig 路径级鉴权回归 - R5 守护 topup/checkin 不误落 ADMIN 门槛")
class RedeemPathSecurityRegressionTest {

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    private RedeemCodeUseCase redeemCodeUseCase;
    @Autowired
    private DailyCheckinUseCase dailyCheckinUseCase;
    @Autowired
    private QueryCheckinStatusUseCase queryCheckinStatusUseCase;
    @Autowired
    private GenerateRedemptionsUseCase generateRedemptionsUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        when(redeemCodeUseCase.redeem(anyLong(), anyString())).thenReturn(Quota.of(100L));
        when(dailyCheckinUseCase.checkin(anyLong()))
                .thenReturn(new DailyCheckinUseCase.CheckinResult(50L));
        when(queryCheckinStatusUseCase.query(anyLong(), any()))
                .thenReturn(CheckinStats.of(0L, 0L, 0, false, List.of()));
        when(generateRedemptionsUseCase.generate(anyInt(), any()))
                .thenReturn(List.of("K".repeat(32)));
    }

    private Authentication asRole(ActorRole role) {
        return new ActorAuthenticationToken(new AuthenticatedActor(1L, "tester", role));
    }

    // ===== 核心回归：USER self-scope 端点不得被路径级 ADMIN 门槛误拦成 403 =====

    @Test
    @DisplayName("普通用户 POST /api/user/topup → 放行至控制器（200，非 403，回归 0cbe497）")
    void commonUserTopupNotForbidden() throws Exception {
        mockMvc.perform(post("/api/user/topup")
                        .with(authentication(asRole(ActorRole.COMMON)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"ANY-KEY\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("普通用户 POST /api/user/checkin → 放行至控制器（200，非 403，同类已修 bug 不回归）")
    void commonUserCheckinPostNotForbidden() throws Exception {
        mockMvc.perform(post("/api/user/checkin")
                        .with(authentication(asRole(ActorRole.COMMON))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("普通用户 GET /api/user/checkin → 放行至控制器（200，非 403）")
    void commonUserCheckinGetNotForbidden() throws Exception {
        mockMvc.perform(get("/api/user/checkin")
                        .with(authentication(asRole(ActorRole.COMMON))))
                .andExpect(status().isOk());
    }

    // ===== 未登录：受保护端点须被拦（401/403），绝不放行 =====

    @Test
    @DisplayName("未登录 POST /api/user/topup → 被拦（401/403），不触达控制器")
    void unauthenticatedTopupBlocked() throws Exception {
        mockMvc.perform(post("/api/user/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"ANY-KEY\"}"))
                .andExpect(blockedAsUnauthorizedOrForbidden());
    }

    @Test
    @DisplayName("未登录 POST /api/user/checkin → 被拦（401/403）")
    void unauthenticatedCheckinBlocked() throws Exception {
        mockMvc.perform(post("/api/user/checkin"))
                .andExpect(blockedAsUnauthorizedOrForbidden());
    }

    // ===== 鉴权不削弱：真正的 ADMIN 端点仍须挡住普通用户（确认修复未开后门）=====

    @Test
    @DisplayName("普通用户 GET /api/user/（管理端列表）→ 仍 403（路径级 ADMIN 门槛未削弱）")
    void commonUserAdminListStillForbidden() throws Exception {
        mockMvc.perform(get("/api/user/")
                        .with(authentication(asRole(ActorRole.COMMON))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("普通用户 POST /api/redemption/（管理端生成）→ 仍 403（方法级 ADMIN 门槛兜底）")
    void commonUserRedemptionGenerateStillForbidden() throws Exception {
        mockMvc.perform(post("/api/redemption/")
                        .with(authentication(asRole(ActorRole.COMMON)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quota\":100,\"count\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin POST /api/redemption/（管理端生成）→ 放行至控制器（200，非 403）")
    void adminRedemptionGenerateNotForbidden() throws Exception {
        mockMvc.perform(post("/api/redemption/")
                        .with(authentication(asRole(ActorRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quota\":100,\"count\":1}"))
                .andExpect(status().isOk());
    }

    /** 受保护端点对未认证请求的合法拦截态：401（认证缺失）或 403（被授权层拒），均「未放行」。 */
    private static ResultMatcher blockedAsUnauthorizedOrForbidden() {
        return result -> {
            int sc = result.getResponse().getStatus();
            assertTrue(sc == 401 || sc == 403,
                    "未登录访问受保护端点应被拦为 401/403，实际 " + sc);
        };
    }

    /**
     * 自包含最小 Web 上下文：真实 {@link SecurityConfig}（被测对象）+ MVC 装配（拦截器/解析器/控制器/
     * 异常翻译）+ 过滤器叶子依赖桩。不引导 {@code NexaApplication} 全量扫描，不连 DB。
     */
    @EnableWebSecurity
    @EnableWebMvc
    @Configuration
    @Import({SecurityConfig.class, SecurityWebMvcConfig.class})
    static class TestWebContext {

        // --- 真实 SecurityConfig 的构造依赖（用桩，过滤器仅需可构造并随链路运行）---

        @Bean
        CorsProperties corsProperties() {
            CorsProperties p = new CorsProperties();
            p.setAllowedOrigins(List.of("http://localhost:3100"));
            return p;
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            // 恒空解析：测试身份由 springSecurity() 的 authentication() 后置器注入，过滤器不参与解析。
            return new JwtAuthenticationFilter(rawCredential -> Optional.empty());
        }

        @Bean
        RelayApiKeyAuthenticationFilter relayApiKeyAuthenticationFilter() {
            // 仅作用 /v1/**，本测不触发；token 仓储用 mock 满足构造。
            return new RelayApiKeyAuthenticationFilter(mock(TokenRepository.class));
        }

        // --- 方法级鉴权拦截器 + @CurrentActor 解析器（SecurityWebMvcConfig 据此注册）---

        @Bean
        RequireRoleInterceptor requireRoleInterceptor() {
            return new RequireRoleInterceptor();
        }

        @Bean
        com.nexa.shared.security.interfaces.web.CurrentActorArgumentResolver currentActorArgumentResolver() {
            return new com.nexa.shared.security.interfaces.web.CurrentActorArgumentResolver();
        }

        // --- 异常翻译（领域异常/越权 → HTTP 状态码 + 错误信封）---

        @Bean
        BillingExceptionHandler billingExceptionHandler() {
            return new BillingExceptionHandler();
        }

        @Bean
        SecurityExceptionHandler securityExceptionHandler() {
            return new SecurityExceptionHandler();
        }

        // --- 被测端点的控制器 + 用例（用例 mock，本测只验鉴权放行/拦截）---

        @Bean
        RedeemCodeUseCase redeemCodeUseCase() {
            return mock(RedeemCodeUseCase.class);
        }

        @Bean
        ListRedemptionsUseCase listRedemptionsUseCase() {
            return mock(ListRedemptionsUseCase.class);
        }

        @Bean
        GenerateRedemptionsUseCase generateRedemptionsUseCase() {
            return mock(GenerateRedemptionsUseCase.class);
        }

        @Bean
        DailyCheckinUseCase dailyCheckinUseCase() {
            return mock(DailyCheckinUseCase.class);
        }

        @Bean
        QueryCheckinStatusUseCase queryCheckinStatusUseCase() {
            return mock(QueryCheckinStatusUseCase.class);
        }

        @Bean
        RedeemController redeemController(RedeemCodeUseCase useCase) {
            return new RedeemController(useCase);
        }

        @Bean
        RedemptionController redemptionController(ListRedemptionsUseCase list,
                                                  GenerateRedemptionsUseCase generate) {
            return new RedemptionController(list, generate);
        }

        @Bean
        CheckinController checkinController(DailyCheckinUseCase daily,
                                            QueryCheckinStatusUseCase query) {
            return new CheckinController(daily, query);
        }
    }
}
