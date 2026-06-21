package com.nexa.channel.infrastructure.probe;

import com.nexa.channel.application.port.ChannelProbeClient;
import com.nexa.channel.application.port.CodexUsageProbe;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.model.ChannelTestResult;
import com.nexa.channel.domain.vo.CodexKeyCredential;
import com.nexa.channel.domain.vo.CodexUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 渠道上游网关端口的占位实现（基础设施层 stub adapter，F-2017/F-2018/F-2026/F-2027）。
 *
 * <p><b>切片边界说明（诚实标注）</b>：本片（W2 渠道管理）聚焦渠道 CRUD/搜索/批量/按 tag 启停的
 * 完整领域与持久化落地。真实上游交互（向各上游 provider 发 chat 探测、查计费余额、列模型、
 * 调 Ollama API）涉及大量 provider 适配与网络 IO，按「功能切小、优先保证编译通过」原则，本类先以
 * 行为合理的占位实现承载端口契约：</p>
 * <ul>
 *   <li>{@link #test}：返回成功占位结果（耗时占位 0），不发真实请求——真实现替换为按渠道 type 选适配器探测。</li>
 *   <li>{@link #queryBalance}：返回 0（占位）——真实现按 type 调上游计费 API。</li>
 *   <li>{@link #fetchUpstreamModels}：回显渠道已声明 models 拆分（占位）——真实现调上游 /v1/models。</li>
 *   <li>{@link #ollamaPull}/{@link #ollamaDelete}：空操作（占位）——真实现调 Ollama /api/pull|/api/delete。</li>
 *   <li>{@link #ollamaVersion}：返回占位版本——真实现调 Ollama /api/version。</li>
 * </ul>
 *
 * <p>真实接入时仅替换本 adapter，应用层/领域层（依赖 {@link ChannelProbeClient} 端口）无需改动
 * （DDD 防腐层价值，backend-engineer §2.3）。所有真实失败路径应抛
 * {@code ChannelUpstreamException}（端口契约已声明，接口层映射 502）。</p>
 */
@Component
public class StubChannelProbeClient implements ChannelProbeClient {

    /** {@inheritDoc} */
    @Override
    public ChannelTestResult test(Channel channel, String model) {
        // 占位：不发真实请求，返回成功 + 0 耗时。真实现按 channel.type() 选适配器发 chat 探测并计时。
        return ChannelTestResult.success(0.0);
    }

    /** {@inheritDoc} */
    @Override
    public BigDecimal queryBalance(Channel channel) {
        // 占位：返回 0。真实现按 type 调上游计费查询接口（OpenAI/Azure/自定义等各异）。
        return BigDecimal.ZERO;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> fetchUpstreamModels(Channel channel) {
        // 占位：回显渠道已声明 models 拆分（保序去空白）。真实现调上游 GET /v1/models。
        String models = channel.models();
        if (models == null || models.isBlank()) {
            return List.of();
        }
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public void ollamaPull(Channel channel, String model) {
        // 占位：空操作。真实现 POST {baseUrl}/api/pull {name: model}。
    }

    /** {@inheritDoc} */
    @Override
    public void ollamaDelete(Channel channel, String model) {
        // 占位：空操作。真实现 DELETE {baseUrl}/api/delete {name: model}。
    }

    /** {@inheritDoc} */
    @Override
    public String ollamaVersion(Channel channel) {
        // 占位：返回固定版本。真实现 GET {baseUrl}/api/version 取 version 字段。
        return "unknown";
    }

    /** {@inheritDoc} */
    @Override
    public CodexUsageProbe queryCodexUsage(Channel channel) {
        // 占位：解析凭证（触发 access_token/account_id 缺失校验 → 400），返回空用量、不刷新。
        // 真实现：用 cred.accessToken()/cred.accountId() 调上游 wham 用量接口；上游 401/403 且
        // cred.canRefresh() 时 RefreshCodexOAuthToken 重试，刷新成功经 withRefreshedKey 回传新 key。
        CodexKeyCredential cred = channel.codexCredential();
        // 引用 cred 以表达「凭证已解析校验通过」这一前置（真实现据此发上游请求）。
        assert cred.accessToken() != null;
        return CodexUsageProbe.ofUsage(CodexUsage.of("{}"));
    }
}
