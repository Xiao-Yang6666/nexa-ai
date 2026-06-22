package com.nexa.account.interfaces.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号注册+登录端到端集成测试（{@code @SpringBootTest} 起全栈，连真 PostgreSQL）。
 *
 * <p>验证：Flyway 在真 PG 建表后，注册→登录走通整条链路（接口层↔用例↔仓储↔真库），
 * 且登录响应 {@code data}（UserView）<b>零敏感字段泄露</b>（无 password/access_token）。
 * 用 MockMvc 发真实 HTTP 语义请求；测试数据用随机用户名隔离，{@code @AfterEach} 物理清理。</p>
 *
 * <p>稳定性：若 CI 环境无法连 PG，本类会在上下文启动阶段失败——可单独排除只跑单测
 * （{@code mvn test -Dtest='!*IT'}）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UserController 集成测试 - 注册+登录走真 PG")
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本次用例创建的用户名（随机，避免与历史数据/并发用例冲突）。 */
    private String username;

    @AfterEach
    void cleanUp() {
        if (username != null) {
            // 物理删除测试用户，保持库干净（集成测试不留垃圾数据）。
            jdbcTemplate.update("DELETE FROM users WHERE username = ?", username);
        }
    }

    @Test
    @DisplayName("注册成功后用同凭证登录成功，且登录返回的 UserView 不含任何敏感字段")
    void register_then_login_succeeds_and_userview_has_no_secrets() throws Exception {
        username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "password123";

        String registerBody = """
                {"username":"%s","password":"%s","email":"%s@example.com"}
                """.formatted(username, password, username);

        // --- 注册：期望 200 + success=true ---
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        String loginBody = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);

        // --- 登录：期望 200 + success=true + data.username 匹配 ---
        String responseJson = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is(username)))
                .andReturn().getResponse().getContentAsString();

        // --- 零敏感字段断言：data 节点绝不含 password/password_hash/access_token/token ---
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode data = root.get("data");
        org.junit.jupiter.api.Assertions.assertNotNull(data, "登录应返回 data(UserView)");
        for (String forbidden : new String[]{
                "password", "password_hash", "passwordHash", "access_token", "accessToken", "token"}) {
            org.junit.jupiter.api.Assertions.assertFalse(data.has(forbidden),
                    "UserView 不得泄露敏感字段: " + forbidden);
        }
    }

    @Test
    @DisplayName("错误密码登录返回 400 + success=false")
    void login_wrongPassword_returns400() throws Exception {
        username = "it_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "password123";

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrongpassword"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
