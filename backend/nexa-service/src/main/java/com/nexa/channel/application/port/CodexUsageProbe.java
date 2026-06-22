package com.nexa.channel.application.port;

import com.nexa.channel.domain.vo.CodexUsage;

/**
 * Codex 上游用量探测结果（应用层端口返回载体，F-4045）。
 *
 * <p>承载基础设施层向上游 wham 用量接口查询的结果，并把「是否发生了 OAuth token 自动刷新」
 * 与「刷新后的新 key」一并回传应用层——使「回写渠道 key + 判定缓存重建」这一<b>有持久化副作用</b>
 * 的编排留在应用层（事务边界、聚合状态变更归应用/领域），而非散落在 infra adapter 内（backend-engineer
 * §2.1 依赖方向：副作用编排向内收口，infra 只做上游 IO + 协议归一）。</p>
 *
 * <p>语义约定：
 * <ul>
 *   <li>{@code usage} —— wham 用量结果（{@link CodexUsage}，含 rawPayload + tokenRefreshed 标记）。</li>
 *   <li>{@code refreshedKey} —— 仅当上游 401/403 触发了 RefreshCodexOAuthToken 且刷新成功时非空
 *       （应用层据此 {@code channel.refreshCodexKey} 回写并按 status∈{1,3} 决定 InitChannelCache）；
 *       未刷新则为 null。</li>
 * </ul>
 * 凭证段不下发视图：{@code refreshedKey} 仅供应用层持久化用，绝不进 AdminView。</p>
 *
 * @param usage        wham 用量结果（非空）
 * @param refreshedKey 刷新后的新 key 原文（仅刷新发生时非空，敏感，仅持久化用）
 */
public record CodexUsageProbe(CodexUsage usage, String refreshedKey) {

    /**
     * 构造未触发刷新的探测结果（一次查询命中、未遇 401/403）。
     *
     * @param usage wham 用量结果
     * @return 探测结果（refreshedKey=null）
     */
    public static CodexUsageProbe ofUsage(CodexUsage usage) {
        return new CodexUsageProbe(usage, null);
    }

    /**
     * 构造经凭证刷新后重试成功的探测结果（上游先 401/403、刷新 token 后二次查询成功）。
     *
     * @param usage        重试后 wham 用量结果（应标记 tokenRefreshed=true）
     * @param refreshedKey 刷新后的新 key 原文（供应用层回写渠道）
     * @return 探测结果（携带 refreshedKey）
     */
    public static CodexUsageProbe withRefreshedKey(CodexUsage usage, String refreshedKey) {
        return new CodexUsageProbe(usage, refreshedKey);
    }

    /** @return 本次探测是否触发了凭证自动刷新（refreshedKey 非空白） */
    public boolean keyRefreshed() {
        return refreshedKey != null && !refreshedKey.isBlank();
    }
}
