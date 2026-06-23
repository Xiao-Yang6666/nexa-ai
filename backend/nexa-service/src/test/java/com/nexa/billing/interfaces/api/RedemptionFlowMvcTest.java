package com.nexa.billing.interfaces.api;

import com.nexa.billing.application.GenerateRedemptionsUseCase;
import com.nexa.billing.application.ListRedemptionsUseCase;
import com.nexa.billing.application.RedeemCodeUseCase;
import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.billing.domain.model.Redemption;
import com.nexa.billing.domain.repository.RedemptionRepository;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.shared.security.domain.rbac.ActorRole;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.infrastructure.auth.ActorAuthenticationToken;
import com.nexa.shared.security.interfaces.api.SecurityExceptionHandler;
import com.nexa.shared.security.interfaces.web.CurrentActorArgumentResolver;
import com.nexa.shared.security.interfaces.web.RequireRoleInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 兑换码闭环 HTTP 回归 MockMvc（R5-redeem 加固）——生成（admin）+ 兑换（user）端到端走真用例。
 *
 * <p>standaloneSetup 起真实 MVC dispatch + 装载 {@link RequireRoleInterceptor}（方法级 {@code @RequireRole}
 * 鉴权，与生产同款）+ {@link CurrentActorArgumentResolver}（注入认证主体）+ {@link BillingExceptionHandler}/
 * {@link SecurityExceptionHandler}（领域异常/越权 → HTTP 状态码）。链路等价 curl 打到
 * {@code /api/redemption/} 与 {@code /api/user/topup}：拦截器鉴权 → 控制器 → 真实
 * {@link GenerateRedemptionsUseCase}/{@link RedeemCodeUseCase} → 内存仓储/账户桩。</p>
 *
 * <p>用例为真实对象（非 mock），仅最外层端口（{@link RedemptionRepository}/{@link UserQuotaAccount}）
 * 用内存桩，验证活体语义：admin 生成返回明文 key 且落库；非 admin 生成 403；普通用户合法兑换
 * 200 且配额真到账；重复/无效/过期兑换 400 且不重复入账。</p>
 */
@DisplayName("兑换码闭环 HTTP 回归 - R5 生成(admin)+兑换(user)走真用例")
class RedemptionFlowMvcTest {

    private static final long NOW = 1_700_000_000L;

    private MockMvc mockMvc;
    private InMemoryRedemptionRepository redemptionRepository;
    private RecordingQuotaAccount quotaAccount;

    @BeforeEach
    void setUp() {
        redemptionRepository = new InMemoryRedemptionRepository();
        quotaAccount = new RecordingQuotaAccount();

        RedemptionController redemptionController = new RedemptionController(
                new ListRedemptionsUseCase(redemptionRepository),
                new GenerateRedemptionsUseCase(redemptionRepository));
        RedeemController redeemController = new RedeemController(
                new RedeemCodeUseCase(redemptionRepository, quotaAccount));

        mockMvc = MockMvcBuilders.standaloneSetup(redemptionController, redeemController)
                .setCustomArgumentResolvers(new CurrentActorArgumentResolver())
                .addInterceptors(new RequireRoleInterceptor())
                .setControllerAdvice(new BillingExceptionHandler(), new SecurityExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(ActorRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(new AuthenticatedActor(7L, "tester", role)));
    }

    // ---------- 生成端点（POST /api/redemption/，AdminAuth）----------

    @Test
    @DisplayName("admin POST /api/redemption/ → 200 + 返回明文 key 且批量落库")
    void adminGenerateReturns200WithPlaintextKeys() throws Exception {
        authenticateAs(ActorRole.ADMIN);
        mockMvc.perform(post("/api/redemption/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"batch-A\",\"quota\":500,\"count\":3,\"expired_time\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(3)));
        assertEquals(3, redemptionRepository.count(), "生成的兑换码须批量落库");
    }

    @Test
    @DisplayName("非 admin（common）POST /api/redemption/ → 403 且不落库")
    void nonAdminGenerateReturns403() throws Exception {
        authenticateAs(ActorRole.COMMON);
        mockMvc.perform(post("/api/redemption/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"quota\":100,\"count\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
        assertEquals(0, redemptionRepository.count(), "越权生成绝不得落库");
    }

    @Test
    @DisplayName("未登录 POST /api/redemption/ → 401")
    void unauthenticatedGenerateReturns401() throws Exception {
        mockMvc.perform(post("/api/redemption/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quota\":100,\"count\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 兑换端点（POST /api/user/topup，sessionAuth/USER）----------

    @Test
    @DisplayName("普通用户合法兑换 → 200 + 配额真到账（走真聚合置已用）")
    void userRedeemValidKeyCreditsQuota() throws Exception {
        Redemption code = Redemption.create(1, "n", Quota.of(10_000L), 0L, NOW);
        redemptionRepository.save(code);

        authenticateAs(ActorRole.COMMON);
        mockMvc.perform(post("/api/user/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + code.key() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.quota", is(10000)));
        assertEquals(10_000L, quotaAccount.creditedTo(7L), "兑换面额须真入账到兑换人");
    }

    @Test
    @DisplayName("重复兑换已用码 → 400 且不重复入账")
    void doubleRedeemRejected() throws Exception {
        Redemption code = Redemption.create(1, "n", Quota.of(300L), 0L, NOW);
        redemptionRepository.save(code);

        authenticateAs(ActorRole.COMMON);
        String body = "{\"key\":\"" + code.key() + "\"}";
        mockMvc.perform(post("/api/user/topup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/user/topup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
        assertEquals(300L, quotaAccount.creditedTo(7L), "重复兑换不得二次入账");
    }

    @Test
    @DisplayName("无效/不存在码 → 400")
    void invalidKeyRejected() throws Exception {
        authenticateAs(ActorRole.COMMON);
        mockMvc.perform(post("/api/user/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"NOPE-NOT-A-REAL-KEY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
        assertEquals(0L, quotaAccount.creditedTo(7L), "无效码不得入账");
    }

    @Test
    @DisplayName("已过期码 → 400")
    void expiredKeyRejected() throws Exception {
        Redemption code = Redemption.create(1, "n", Quota.of(300L), NOW + 100, NOW);
        redemptionRepository.save(code);

        authenticateAs(ActorRole.COMMON);
        mockMvc.perform(post("/api/user/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + code.key() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
        assertEquals(0L, quotaAccount.creditedTo(7L), "过期码不得入账");
    }

    @Test
    @DisplayName("未登录兑换 → 401")
    void unauthenticatedRedeemReturns401() throws Exception {
        mockMvc.perform(post("/api/user/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"ANY\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 内存桩 ----------

    /** 内存兑换码仓储桩（按 key 索引；不起 DB，验证用例→聚合真实链路）。 */
    private static final class InMemoryRedemptionRepository implements RedemptionRepository {
        private final List<Redemption> store = new ArrayList<>();
        private final AtomicLong seq = new AtomicLong();

        @Override
        public Optional<Redemption> findByKey(String key) {
            return store.stream().filter(r -> r.key().equals(key)).findFirst();
        }

        @Override
        public Redemption save(Redemption redemption) {
            if (redemption.id() == null) {
                redemption.assignId(seq.incrementAndGet());
                store.add(redemption);
            }
            return redemption;
        }

        @Override
        public List<Redemption> saveAll(List<Redemption> redemptions) {
            redemptions.forEach(this::save);
            return redemptions;
        }

        @Override
        public Page<Redemption> findPage(int page, int pageSize) {
            return new Page<>(List.copyOf(store), store.size(), page, pageSize);
        }

        int count() {
            return store.size();
        }
    }

    /** 内存用户额度账户桩（累计入账，验证「配额真到账」）。 */
    private static final class RecordingQuotaAccount implements UserQuotaAccount {
        private final java.util.Map<Long, Long> credited = new java.util.HashMap<>();

        @Override
        public void credit(long userId, Quota amount) {
            credited.merge(userId, amount.value(), Long::sum);
        }

        @Override
        public void debit(long userId, Quota amount) {
            credited.merge(userId, -amount.value(), Long::sum);
        }

        @Override
        public Quota balanceOf(long userId) {
            return Quota.of(Math.max(0L, credited.getOrDefault(userId, 0L)));
        }

        long creditedTo(long userId) {
            return credited.getOrDefault(userId, 0L);
        }
    }
}
