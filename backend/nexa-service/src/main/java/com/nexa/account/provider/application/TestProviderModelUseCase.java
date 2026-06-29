package com.nexa.account.provider.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.account.provider.application.port.ProviderModelProbePort.ProviderProbeException;
import com.nexa.account.provider.application.port.ProviderModelTestPort;
import com.nexa.account.provider.application.port.ProviderModelTestPort.ProviderModelTestResult;
import com.nexa.account.provider.domain.exception.AccountNotFoundException;
import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;

/**
 * 模型连通性测试用例（账号域，AdminAuth 在账号操作里点「测试」调用）。
 *
 * <p>薄编排：按 id 载入已保存账号 → 从 credentials JSON（{@code {"key":"..."}}）取上游 API Key
 * → 用账号的 platform/baseUrl + 指定 model 发一次真实非流式聊天补全（{@link ProviderModelTestPort}）。
 * 用<b>账号已存的凭证</b>测试（key 不经前端往返，与 relay 真实转发取 key 路径一致），验证
 * 「这个账号 + 这个模型」能否跑通。</p>
 *
 * <p><b>安全</b>：apiKey 仅在服务端从 credentials 解出、注入上游请求头，绝不回前端、绝不进日志。
 * 账号缺失抛 {@link AccountNotFoundException}（404）；凭证缺失/上游失败抛
 * {@link ProviderProbeException}（502），均不回显 key。</p>
 */
@Service
public class TestProviderModelUseCase {

    private final AccountRepository accountRepository;
    private final ProviderModelTestPort testPort;
    private final ObjectMapper objectMapper;

    /**
     * @param accountRepository 账号仓储
     * @param testPort          上游模型测试端口
     * @param objectMapper      JSON 解析（从 credentials 取 key）
     */
    public TestProviderModelUseCase(AccountRepository accountRepository,
                                    ProviderModelTestPort testPort,
                                    ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.testPort = testPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 对指定账号的某模型发一次连通性测试。
     *
     * @param accountId 账号 id（须已保存）
     * @param model     要测试的模型 ID（必填）
     * @param prompt    测试提示词（可空 → 适配器回落默认短提示）
     * @return 测试结果（耗时 + 回复片段）
     * @throws AccountNotFoundException 账号不存在
     * @throws ProviderProbeException   凭证缺失 / 上游失败 / 解析失败
     */
    public ProviderModelTestResult test(long accountId, String model, String prompt) {
        if (model == null || model.isBlank()) {
            throw new ProviderProbeException("缺少要测试的模型");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        String apiKey = extractKey(account.credentials());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ProviderProbeException("该账号未配置 API Key，无法测试");
        }
        return testPort.testChat(
                account.platform(), account.baseUrl(), apiKey, model.trim(), prompt);
    }

    /**
     * 对指定账号的某模型发一次<b>流式</b>连通性测试，逐 token 增量回调。
     *
     * <p>与 {@link #test} 同样从账号已存凭证取 key，差别仅是走流式端口、增量回调给监听器
     * （接口层把回调逐条写成 SSE 事件给前端逐字显示）。首片前的失败正常上抛
     * （404 / 502，由接口层翻译）；首片已发后中断由端口实现消化。</p>
     *
     * @param accountId 账号 id（须已保存）
     * @param model     要测试的模型 ID（必填）
     * @param prompt    测试提示词（可空 → 适配器回落默认短提示）
     * @param listener  增量 + 收束回调
     * @throws AccountNotFoundException 账号不存在
     * @throws ProviderProbeException   凭证缺失 / 上游失败 / 解析失败（首片前）
     */
    public void testStream(long accountId, String model, String prompt,
                           ProviderModelTestPort.TestStreamListener listener) {
        if (model == null || model.isBlank()) {
            throw new ProviderProbeException("缺少要测试的模型");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        String apiKey = extractKey(account.credentials());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ProviderProbeException("该账号未配置 API Key，无法测试");
        }
        testPort.testChatStream(
                account.platform(), account.baseUrl(), apiKey, model.trim(), prompt, listener);
    }

    /** 从账号 credentials JSON（{@code {"key":"..."}}）提取上游 API Key；缺失/非法返回 null（不回显）。 */
    private String extractKey(String credentials) {
        if (credentials == null || credentials.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(credentials).path("key");
            return node.isTextual() ? node.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
