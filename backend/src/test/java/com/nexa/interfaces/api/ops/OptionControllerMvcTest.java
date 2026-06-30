package com.nexa.interfaces.api.ops;

import com.nexa.application.ops.compliance.ConfirmPaymentComplianceUseCase;
import com.nexa.application.ops.option.ListOptionsUseCase;
import com.nexa.application.ops.option.UpdateOptionUseCase;
import com.nexa.domain.ops.option.Option;
import com.nexa.domain.ops.option.OptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OptionController HTTP 链路 MockMvc 回归（R3-01 系统配置面板接通）。
 *
 * <p>standaloneSetup 起真实 MVC dispatch + {@link OpsExceptionHandler}（绕过 ROOT 拦截器，
 * 聚焦协议翻译层），等价于 curl 打到 {@code /api/option/} 时的链路：
 * 控制器 → 真实 {@link UpdateOptionUseCase}/{@link ListOptionsUseCase} →
 * {@code OptionRegistry} 领域校验 → 异常翻译 → JSON 信封。</p>
 *
 * <p>用内存桩仓储（{@link OptionRepository}）承载写读，无需真库；验证 R3-01 三条活体语义：
 * 非法布尔值 → 400 + 中文错误；合法值 → 200 + 回读正确；敏感键 smtp.passwordSecret 列表剔除值。</p>
 */
@DisplayName("OptionController HTTP 回归 - R3-01 系统配置面板校验/剔除")
class OptionControllerMvcTest {

    private MockMvc mockMvc;
    private Map<String, String> store;

    @BeforeEach
    void setUp() {
        store = new ConcurrentHashMap<>();
        OptionRepository repo = new OptionRepository() {
            @Override
            public List<Option> findAll() {
                return store.entrySet().stream()
                        .map(e -> Option.of(e.getKey(), e.getValue()))
                        .toList();
            }

            @Override
            public Optional<Option> findByKey(String key) {
                String v = store.get(key);
                return v == null ? Optional.empty() : Optional.of(Option.of(key, v));
            }

            @Override
            public void save(Option option) {
                store.put(option.keyName(), option.value());
            }

            @Override
            public void deleteByKey(String key) {
                store.remove(key);
            }
        };

        OptionController controller = new OptionController(
                new ListOptionsUseCase(repo),
                new UpdateOptionUseCase(repo),
                org.mockito.Mockito.mock(ConfirmPaymentComplianceUseCase.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OpsExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PUT 非法布尔值（register.invite_only=maybe）→ 400 + 中文错误")
    void putInvalidBooleanReturns400WithChineseMessage() throws Exception {
        mockMvc.perform(put("/api/option/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"register.invite_only\",\"value\":\"maybe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("true")));
    }

    @Test
    @DisplayName("PUT 非法货币（billing.currency=EUR）→ 400 + 中文错误")
    void putInvalidCurrencyReturns400() throws Exception {
        mockMvc.perform(put("/api/option/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"billing.currency\",\"value\":\"EUR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("货币")));
    }

    @Test
    @DisplayName("PUT 合法值 → 200，再 GET 回读正确")
    void putValidThenGetReadsBack() throws Exception {
        mockMvc.perform(put("/api/option/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"register.invite_only\",\"value\":\"true\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(put("/api/option/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"smtp.port\",\"value\":\"587\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/option/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("register.invite_only"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"true\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("smtp.port"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("587"));
    }

    @Test
    @DisplayName("GET 列表剔除敏感键 smtp.passwordSecret 的值（不泄露明文）")
    void getListStripsSensitiveSmtpPassword() throws Exception {
        // 先写入敏感键（PUT 本身直通，值落库）。
        mockMvc.perform(put("/api/option/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"smtp.passwordSecret\",\"value\":\"s3cr3t-plain\"}"))
                .andExpect(status().isOk());
        // 同时写一个非敏感键作对照。
        mockMvc.perform(put("/api/option/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"smtp.host\",\"value\":\"smtp.mailgun.org\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/option/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        // 敏感键的键与值都不应出现在列表（用例层 isSensitive 过滤整条）。
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("s3cr3t-plain"),
                "敏感键明文值绝不得出现在列表响应");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("smtp.passwordSecret"),
                "敏感键整条已被剔除");
        // 非敏感键正常出现。
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("smtp.host"));
    }
}
