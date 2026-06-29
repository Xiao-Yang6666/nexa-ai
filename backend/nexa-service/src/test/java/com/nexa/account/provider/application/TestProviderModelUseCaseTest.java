package com.nexa.account.provider.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.account.provider.application.port.ProviderModelProbePort.ProviderProbeException;
import com.nexa.account.provider.application.port.ProviderModelTestPort;
import com.nexa.account.provider.application.port.ProviderModelTestPort.ProviderModelTestResult;
import com.nexa.account.provider.domain.exception.AccountNotFoundException;
import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import com.nexa.account.provider.domain.vo.Pagination;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型连通性测试用例单测。
 *
 * <p>用桩仓储 + 桩端口验证编排：从账号 credentials JSON 取 key 透传给测试端口、
 * 账号缺失抛 404、模型为空 / 凭证缺失抛探测异常（502），且 apiKey 不出现在异常信息里。</p>
 */
class TestProviderModelUseCaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 用持久化重建工厂造一个带凭证的账号（不触发创建不变量）。 */
    private Account accountWithKey(String credentialsJson) {
        return Account.rehydrate(
                7L, "测试账号", "openai", "api_key", credentialsJson, "https://api.openai.com",
                3, 50, "active",
                null, null, null, null, true, BigDecimal.ONE,
                null, 0, null, false,
                null, null, null, null, null,
                List.of(), null, null);
    }

    /** 用 testChat 行为构造一个端口桩（testChatStream 本测不触达，留空实现）。 */
    private ProviderModelTestPort chatPort(ChatFn fn) {
        return new ProviderModelTestPort() {
            @Override
            public ProviderModelTestResult testChat(String platform, String baseUrl, String apiKey,
                                                    String model, String prompt) {
                return fn.apply(platform, baseUrl, apiKey, model, prompt);
            }

            @Override
            public void testChatStream(String platform, String baseUrl, String apiKey,
                                       String model, String prompt, TestStreamListener listener) {
                throw new UnsupportedOperationException("本测不触达流式");
            }
        };
    }

    /** testChat 五元函数式接口（测试桩用）。 */
    @FunctionalInterface
    private interface ChatFn {
        ProviderModelTestResult apply(String platform, String baseUrl, String apiKey,
                                      String model, String prompt);
    }

    @Test
    void test_extractsKeyFromCredentialsAndForwardsToPort() {
        Account account = accountWithKey("{\"key\":\"sk-secret\"}");
        AccountRepository repo = stubRepo(account);

        AtomicReference<String> seenKey = new AtomicReference<>();
        AtomicReference<String> seenModel = new AtomicReference<>();
        ProviderModelTestPort port = chatPort((platform, baseUrl, apiKey, model, prompt) -> {
            seenKey.set(apiKey);
            seenModel.set(model);
            return new ProviderModelTestResult(123L, "OK");
        });

        TestProviderModelUseCase useCase = new TestProviderModelUseCase(repo, port, objectMapper);
        ProviderModelTestResult result = useCase.test(7L, "gpt-4o-mini", "ping");

        assertThat(seenKey.get()).isEqualTo("sk-secret");
        assertThat(seenModel.get()).isEqualTo("gpt-4o-mini");
        assertThat(result.latencyMs()).isEqualTo(123L);
        assertThat(result.reply()).isEqualTo("OK");
    }

    @Test
    void test_throwsNotFound_whenAccountMissing() {
        AccountRepository emptyRepo = stubRepo(null);
        ProviderModelTestPort port = chatPort((p, b, k, m, pr) -> new ProviderModelTestResult(0, ""));

        TestProviderModelUseCase useCase = new TestProviderModelUseCase(emptyRepo, port, objectMapper);

        assertThatThrownBy(() -> useCase.test(99L, "gpt-4o", null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void test_throwsProbeException_whenModelBlank() {
        TestProviderModelUseCase useCase = new TestProviderModelUseCase(
                stubRepo(accountWithKey("{\"key\":\"sk-x\"}")),
                chatPort((p, b, k, m, pr) -> new ProviderModelTestResult(0, "")),
                objectMapper);

        assertThatThrownBy(() -> useCase.test(7L, "  ", null))
                .isInstanceOf(ProviderProbeException.class);
    }

    @Test
    void test_throwsProbeException_whenCredentialsMissingKey_andNeverLeaksKey() {
        TestProviderModelUseCase useCase = new TestProviderModelUseCase(
                stubRepo(accountWithKey("{}")),
                chatPort((p, b, k, m, pr) -> new ProviderModelTestResult(0, "")),
                objectMapper);

        assertThatThrownBy(() -> useCase.test(7L, "gpt-4o", null))
                .isInstanceOf(ProviderProbeException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void testStream_extractsKeyAndForwardsDeltasToListener() {
        Account account = accountWithKey("{\"key\":\"sk-stream\"}");
        AtomicReference<String> seenKey = new AtomicReference<>();
        ProviderModelTestPort port = new ProviderModelTestPort() {
            @Override
            public ProviderModelTestResult testChat(String platform, String baseUrl, String apiKey,
                                                    String model, String prompt) {
                throw new UnsupportedOperationException("本测走流式");
            }

            @Override
            public void testChatStream(String platform, String baseUrl, String apiKey,
                                       String model, String prompt, TestStreamListener listener) {
                seenKey.set(apiKey);
                listener.onDelta("你好");
                listener.onDelta("，OK");
                listener.onComplete(new ProviderModelTestResult(42L, "你好，OK"));
            }
        };

        StringBuilder acc = new StringBuilder();
        AtomicReference<Long> doneLatency = new AtomicReference<>();
        TestProviderModelUseCase useCase = new TestProviderModelUseCase(
                stubRepo(account), port, objectMapper);
        useCase.testStream(7L, "gpt-4o", "hi", new ProviderModelTestPort.TestStreamListener() {
            @Override
            public void onDelta(String text) {
                acc.append(text);
            }

            @Override
            public void onComplete(ProviderModelTestResult result) {
                doneLatency.set(result.latencyMs());
            }
        });

        assertThat(seenKey.get()).isEqualTo("sk-stream");
        assertThat(acc.toString()).isEqualTo("你好，OK");
        assertThat(doneLatency.get()).isEqualTo(42L);
    }

    /** 仅按本测试用到的 findById 提供桩实现，其余仓储方法返回空/无操作。 */
    private AccountRepository stubRepo(Account account) {
        return new AccountRepository() {
            @Override
            public Account save(Account a) {
                return a;
            }

            @Override
            public Optional<Account> findById(long id) {
                return (account != null && id == 7L) ? Optional.of(account) : Optional.empty();
            }

            @Override
            public List<Account> findPage(String platform, Pagination pagination) {
                return List.of();
            }

            @Override
            public long count(String platform) {
                return 0;
            }

            @Override
            public List<Account> findByPlatform(String platform) {
                return List.of();
            }

            @Override
            public List<Account> findAll() {
                return List.of();
            }

            @Override
            public List<Account> findSchedulable(long now) {
                return List.of();
            }

            @Override
            public List<Account> findSchedulableByGroup(String group, long now) {
                return List.of();
            }

            @Override
            public List<Account> findSchedulableByModel(String model, long now) {
                return List.of();
            }

            @Override
            public void deleteById(long id) {
            }
        };
    }
}
