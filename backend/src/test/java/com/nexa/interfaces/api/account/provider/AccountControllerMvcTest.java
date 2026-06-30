package com.nexa.interfaces.api.account.provider;

import com.nexa.application.account.provider.CreateAccountUseCase;
import com.nexa.application.account.provider.DeleteAccountUseCase;
import com.nexa.application.account.provider.GetAccountUseCase;
import com.nexa.application.account.provider.ListAccountsUseCase;
import com.nexa.application.account.provider.ProbeProviderModelsUseCase;
import com.nexa.application.account.provider.TestProviderModelUseCase;
import com.nexa.application.account.provider.ToggleAccountUseCase;
import com.nexa.application.account.provider.UpdateAccountUseCase;
import com.nexa.domain.account.provider.model.Account;
import com.nexa.domain.account.provider.repository.AccountRepository;
import com.nexa.domain.account.provider.vo.Pagination;
import com.nexa.shared.security.rbac.AuthLevel;
import com.nexa.shared.security.annotation.RequireRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AccountController} HTTP 链路 MockMvc 回归（standaloneSetup，内存桩仓储，无 DB）。
 *
 * <p>起真实 MVC dispatch + {@link ProviderAccountExceptionHandler}（绕过 ADMIN 拦截器，聚焦协议翻译层），
 * 验证 CRUD + 启停活体语义：创建 → 列表/详情回读 → 编辑 → 启停 → 删除 404；非法入参 400；
 * <b>credentials 绝不回显</b>。ADMIN 鉴权由类级 {@link RequireRole} 声明（拦截器统一执行），
 * 本测以注解断言守护「未越权裸奔」契约。</p>
 */
@DisplayName("AccountController HTTP 回归 - 供应商账号 CRUD/启停/凭证脱敏")
class AccountControllerMvcTest {

    private MockMvc mockMvc;

    /** 内存桩仓储：用 Account.create/update 走真实充血逻辑，避免引入 Mockito 行为脚本。 */
    static class InMemoryAccountRepository implements AccountRepository {
        private final ConcurrentHashMap<Long, Account> store = new ConcurrentHashMap<>();
        private final AtomicLong seq = new AtomicLong(0);

        @Override
        public Account save(Account account) {
            if (account.id() == null) {
                account.assignId(seq.incrementAndGet());
            }
            store.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Account> findPage(String platform, Pagination pagination) {
            List<Account> all = new ArrayList<>();
            for (Account a : store.values()) {
                if (platform == null || platform.isBlank() || platform.equals(a.platform())) {
                    all.add(a);
                }
            }
            return all;
        }

        @Override
        public long count(String platform) {
            return findPage(platform, Pagination.of(1, 100)).size();
        }

        @Override
        public List<Account> findByPlatform(String platform) {
            return findPage(platform, Pagination.of(1, 100));
        }

        @Override
        public List<Account> findAll() {
            return new java.util.ArrayList<>(store.values());
        }

        @Override
        public List<Account> findSchedulable(long now) {
            return store.values().stream().filter(a -> a.isSchedulable(now)).toList();
        }

        @Override
        public List<Account> findSchedulableByGroup(String group, long now) {
            if (group == null || group.isBlank()) {
                return List.of();
            }
            return store.values().stream()
                    .filter(a -> a.isSchedulable(now))
                    .filter(a -> a.groups().stream().anyMatch(g -> group.equals(g.group())))
                    .sorted(java.util.Comparator.comparingInt(Account::priority))
                    .toList();
        }

        @Override
        public List<Account> findSchedulableByModel(String model, long now) {
            if (model == null || model.isBlank()) {
                return List.of();
            }
            return store.values().stream()
                    .filter(a -> a.isSchedulable(now))
                    .filter(a -> a.supportsModel(model))
                    .sorted(java.util.Comparator.comparingInt(Account::priority))
                    .toList();
        }

        @Override
        public void deleteById(long id) {
            store.remove(id);
        }
    }

    @BeforeEach
    void setUp() {
        AccountRepository repo = new InMemoryAccountRepository();
        // 探测端口桩：本测聚焦 CRUD/启停链路，探测用例注入不发网络的桩探测端口 + 不落库的桩登记端口即可。
        ProbeProviderModelsUseCase probeUseCase = new ProbeProviderModelsUseCase(
                (platform, baseUrl, apiKey) -> java.util.List.of(),
                names -> 0);
        // 模型测试用例桩：本测聚焦 CRUD/启停链路，注入不发网络的桩测试端口即可。
        TestProviderModelUseCase testModelUseCase = new TestProviderModelUseCase(
                repo,
                new com.nexa.application.account.provider.port.ProviderModelTestPort() {
                    @Override
                    public com.nexa.application.account.provider.port.ProviderModelTestPort
                            .ProviderModelTestResult testChat(String platform, String baseUrl,
                            String apiKey, String model, String prompt) {
                        return new com.nexa.application.account.provider.port.ProviderModelTestPort
                                .ProviderModelTestResult(0L, "");
                    }

                    @Override
                    public void testChatStream(String platform, String baseUrl, String apiKey,
                            String model, String prompt,
                            com.nexa.application.account.provider.port.ProviderModelTestPort
                                    .TestStreamListener listener) {
                        listener.onComplete(new com.nexa.application.account.provider.port
                                .ProviderModelTestPort.ProviderModelTestResult(0L, ""));
                    }
                },
                new com.fasterxml.jackson.databind.ObjectMapper());
        AccountController controller = new AccountController(
                new ListAccountsUseCase(repo),
                new GetAccountUseCase(repo),
                new CreateAccountUseCase(repo),
                new UpdateAccountUseCase(repo),
                new DeleteAccountUseCase(repo),
                new ToggleAccountUseCase(repo),
                probeUseCase,
                testModelUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProviderAccountExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("控制器类级声明 @RequireRole(ADMIN)（非 admin 由拦截器拒为 403，不裸奔）")
    void controllerRequiresAdmin() {
        RequireRole anno = AccountController.class.getAnnotation(RequireRole.class);
        org.junit.jupiter.api.Assertions.assertNotNull(anno, "AccountController 必须声明 @RequireRole");
        assertEquals(AuthLevel.ADMIN, anno.value(), "账号管理必须 ADMIN 鉴权");
    }

    @Test
    @DisplayName("POST 创建 → 200 + success；响应绝不含 credentials")
    void createReturnsViewWithoutCredentials() throws Exception {
        String body = """
                {"name":"acc1","platform":"openai","type":"api_key",
                 "credentials":"{\\"key\\":\\"sk-secret-xyz\\"}","concurrency":5,"priority":80}
                """;
        String resp = mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("acc1")))
                .andExpect(jsonPath("$.data.platform", is("openai")))
                .andExpect(jsonPath("$.data.status", is("active")))
                .andExpect(jsonPath("$.data.concurrency", is(5)))
                .andReturn().getResponse().getContentAsString();

        assertFalse(resp.contains("credentials"), "视图绝不含 credentials 字段");
        assertFalse(resp.contains("sk-secret-xyz"), "凭证明文绝不回显");
    }

    @Test
    @DisplayName("POST 缺 name → 400 + success=false")
    void createMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"openai\",\"type\":\"api_key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("创建后 GET 列表/详情回读，且列表响应不含 credentials")
    void listAndGetReadBack() throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"platform\":\"openai\",\"type\":\"api_key\",\"credentials\":\"{\\\"k\\\":\\\"v\\\"}\"}"))
                .andExpect(status().isOk());

        String list = mockMvc.perform(get("/api/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("credentials"), "列表视图不含 credentials");

        mockMvc.perform(get("/api/admin/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.name", is("a")));
    }

    @Test
    @DisplayName("GET 不存在 id → 404")
    void getMissingReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/accounts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("PUT 编辑 → 200 回读新值")
    void updateReadsBack() throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"platform\":\"openai\",\"type\":\"api_key\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a2\",\"platform\":\"anthropic\",\"type\":\"oauth\",\"priority\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("a2")))
                .andExpect(jsonPath("$.data.platform", is("anthropic")))
                .andExpect(jsonPath("$.data.priority", is(90)));
    }

    @Test
    @DisplayName("PATCH 启停 → status 在 active/disabled 间迁移")
    void toggleTransitions() throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"platform\":\"openai\",\"type\":\"api_key\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/accounts/1/toggle?enable=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("disabled")));

        mockMvc.perform(patch("/api/admin/accounts/1/toggle?enable=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("active")));
    }

    @Test
    @DisplayName("DELETE → 200；再 GET → 404")
    void deleteThenGet404() throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"platform\":\"openai\",\"type\":\"api_key\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/api/admin/accounts/1"))
                .andExpect(status().isNotFound());
    }
}
