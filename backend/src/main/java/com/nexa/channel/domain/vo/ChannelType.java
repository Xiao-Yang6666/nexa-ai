package com.nexa.channel.domain.vo;

import java.util.Set;

/**
 * 渠道类型值对象（封装 type 整数码 + 关键类型语义判定）。
 *
 * <p>领域规则来源：
 * <ul>
 *   <li>F-2017：七类异步渠道（MidJourney/Suno/Kling/Jimeng/DoubaoVideo/Vidu 等）不支持同步连通性测试。</li>
 *   <li>F-2027：Ollama 模型管理（pull/delete/version）仅适用于 Ollama 类型渠道。</li>
 * </ul>
 * new-api 渠道 type 为开放整数枚举（持续新增），本系统不穷举全部 type，仅识别本片业务规则
 * 关心的少数关键 type（Ollama、异步类），其余按通用渠道处理。type 整数与上游兼容，原样持久化。</p>
 *
 * <p>type 码取自 new-api ChannelType 常量（稳定值）。异步渠道集合见 {@link #ASYNC_TYPE_CODES}。</p>
 *
 * @param code 渠道 type 整数码（持久化值，与上游兼容）
 */
public record ChannelType(int code) {

    /** Ollama 渠道 type 码（new-api ChannelTypeOllama=41）。 */
    public static final int OLLAMA = 41;

    /**
     * Codex 渠道 type 码（new-api ChannelTypeOpenAICodex=54）。
     *
     * <p>F-4045 Codex 渠道上游用量查询仅适用于此类渠道（OAuth key 含 access_token/account_id/
     * refresh_token，查 wham 用量；非 Codex 类型拒绝）。</p>
     */
    public static final int CODEX = 54;

    /**
     * 七类异步渠道 type 码集合（不支持同步连通性测试，F-2017）。
     *
     * <p>取自 new-api ChannelType 常量：MidJourney(35) / Suno(36) / Kling(48) /
     * Jimeng(50) / DoubaoVideo(49) / Vidu(47)。这些渠道走异步任务提交，无同步 chat 探测语义。</p>
     */
    public static final Set<Integer> ASYNC_TYPE_CODES = Set.of(35, 36, 47, 48, 49, 50);

    /** @return 是否为 Ollama 渠道（F-2027 Ollama 管理仅此类适用） */
    public boolean isOllama() {
        return code == OLLAMA;
    }

    /** @return 是否为 Codex 渠道（F-4045 Codex 上游用量查询仅此类适用） */
    public boolean isCodex() {
        return code == CODEX;
    }

    /** @return 是否为异步渠道（F-2017 不支持同步连通性测试） */
    public boolean isAsync() {
        return ASYNC_TYPE_CODES.contains(code);
    }
}
