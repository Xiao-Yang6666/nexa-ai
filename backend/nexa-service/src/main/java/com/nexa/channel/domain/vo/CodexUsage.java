package com.nexa.channel.domain.vo;

import java.util.Objects;

/**
 * Codex 渠道上游用量结果值对象（不可变，F-4045 wham 用量查询结果）。
 *
 * <p>领域规则来源：API-ENDPOINTS §5.8「出参 data = &lt;Codex wham 用量数据&gt;」。上游 wham 用量
 * 接口返回的结构属上游约定（字段不稳定、随上游演进），本系统不强解析其内部结构，而以
 * {@code rawPayload}（JSON 原文字符串）原样承载透传给管理端展示——避免为不稳定的上游 schema
 * 维护强类型映射（new-api 同思路：Codex 用量数据透传不解构）。</p>
 *
 * <p>{@code tokenRefreshed} 标记本次查询过程中是否触发了 OAuth token 自动刷新（上游 401/403 且
 * 渠道有 refresh_token 时，infra 会 RefreshCodexOAuthToken 重试并回写渠道 key）。该标记用于
 * 管理端可观测：让管理员知晓「这次查询顺带刷新了凭证」。</p>
 *
 * <p>本值对象不含任何凭证明文（access_token/refresh_token 绝不进用量结果），可安全进 AdminView。</p>
 *
 * @param rawPayload     上游 wham 用量数据原文（JSON 字符串，原样透传，非空——空上游响应归一为 "{}"）
 * @param tokenRefreshed 本次查询是否触发了凭证自动刷新回写（true=上游 401/403 后刷新重试成功）
 */
public record CodexUsage(String rawPayload, boolean tokenRefreshed) {

    /**
     * 紧凑构造器：归一空 payload 为合法空 JSON 对象（保证 data 始终为合法 JSON 而非 null）。
     */
    public CodexUsage {
        if (rawPayload == null || rawPayload.isBlank()) {
            rawPayload = "{}";
        }
    }

    /**
     * 构造未触发刷新的用量结果（一次查询即命中、未遇 401/403）。
     *
     * @param rawPayload 上游用量数据 JSON 原文
     * @return 用量结果（tokenRefreshed=false）
     */
    public static CodexUsage of(String rawPayload) {
        return new CodexUsage(rawPayload, false);
    }

    /**
     * 构造经凭证自动刷新后重试成功的用量结果（上游先 401/403、刷新 token 后二次查询成功）。
     *
     * @param rawPayload 重试后上游用量数据 JSON 原文
     * @return 用量结果（tokenRefreshed=true）
     */
    public static CodexUsage refreshed(String rawPayload) {
        return new CodexUsage(rawPayload, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CodexUsage other)) {
            return false;
        }
        return tokenRefreshed == other.tokenRefreshed && Objects.equals(rawPayload, other.rawPayload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawPayload, tokenRefreshed);
    }
}
