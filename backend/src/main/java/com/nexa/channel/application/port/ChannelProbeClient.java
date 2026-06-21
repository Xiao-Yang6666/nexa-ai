package com.nexa.channel.application.port;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.model.ChannelTestResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 渠道上游网关端口（应用层 / 防腐层接口，F-2017/F-2018/F-2026/F-2027）。
 *
 * <p>DDD 铁律：domain/application 只依赖本端口，不依赖具体 HTTP client / 各上游 SDK
 * （backend-engineer §2.3）。基础设施层实现本接口，封装上游端点 URL、渠道 key 鉴权、
 * 字段映射、错误归一。这样渠道用例可脱离真实上游单测（mock 本端口）。</p>
 *
 * <p>所有方法在上游未配置/连接失败/返回错误时抛
 * {@link com.nexa.channel.domain.exception.ChannelUpstreamException}（接口层映射 502）。
 * 实现绝不回显渠道 key 等凭证（凭证剔除在 infra 内）。</p>
 */
public interface ChannelProbeClient {

    /**
     * 对单渠道发起连通性测试（F-2017）。
     *
     * @param channel 目标渠道（含上游 BaseURL/key/模型集）
     * @param model   指定测试模型（可空→渠道默认模型）
     * @return 测试结果（成功/失败 + 耗时 + 提示）
     */
    ChannelTestResult test(Channel channel, String model);

    /**
     * 查询单渠道上游余额（F-2018）。
     *
     * @param channel 目标渠道
     * @return 最新余额（USD，BigDecimal）
     */
    BigDecimal queryBalance(Channel channel);

    /**
     * 探测单渠道上游支持的模型集（F-2026 fetch，预览，不改渠道 models）。
     *
     * @param channel 目标渠道
     * @return 上游模型名列表（保序）
     */
    List<String> fetchUpstreamModels(Channel channel);

    /**
     * Ollama 渠道拉取模型（F-2027 pull，仅 Ollama 渠道）。
     *
     * @param channel 目标 Ollama 渠道
     * @param model   待拉取模型名
     */
    void ollamaPull(Channel channel, String model);

    /**
     * Ollama 渠道删除模型（F-2027 delete，仅 Ollama 渠道）。
     *
     * @param channel 目标 Ollama 渠道
     * @param model   待删除模型名
     */
    void ollamaDelete(Channel channel, String model);

    /**
     * Ollama 渠道版本查询（F-2027 version，仅 Ollama 渠道）。
     *
     * @param channel 目标 Ollama 渠道
     * @return Ollama 版本号
     */
    String ollamaVersion(Channel channel);

    /**
     * 查询 Codex 渠道上游用量（F-4045，仅 Codex 单 Key 渠道）。
     *
     * <p>实现职责（infra）：① 取 {@link Channel#codexCredential()} 解析出的 access_token/account_id
     * 调上游 wham 用量接口；② 若上游返回 401/403 且渠道有 refresh_token，则 RefreshCodexOAuthToken
     * 刷新令牌后重试，并把刷新后的新 key 通过 {@link CodexUsageProbe#refreshedKey()} 回传
     * （应用层负责回写渠道 key + 按 status∈{1,3} 决定 InitChannelCache）；③ 无 refresh_token 或刷新
     * 仍失败 → 抛 {@link com.nexa.channel.domain.exception.ChannelUpstreamException}（接口层映射 502）。</p>
     *
     * <p>调用前置（由应用层用例保证）：渠道已 {@code ensureCodex()} + {@code ensureSingleKey()}。
     * 实现绝不回显凭证明文（refreshedKey 仅供应用层持久化）。</p>
     *
     * @param channel 目标 Codex 渠道（单 Key）
     * @return 用量探测结果（wham 用量 + 可选刷新后新 key）
     */
    CodexUsageProbe queryCodexUsage(Channel channel);
}
