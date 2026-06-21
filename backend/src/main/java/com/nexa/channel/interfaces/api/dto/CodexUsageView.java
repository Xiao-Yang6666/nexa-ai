package com.nexa.channel.interfaces.api.dto;

import com.nexa.channel.domain.vo.CodexUsage;

/**
 * Codex 渠道上游用量管理视图（AdminView，F-4045，{@code GET /api/channel/{id}/codex/usage}）。
 *
 * <p>承载上游 wham 用量数据原文（{@code usage}，JSON 字符串原样透传——上游 schema 不稳定不强解构）
 * 与本次查询是否触发了凭证自动刷新的标记（{@code token_refreshed}，管理端可观测）。</p>
 *
 * <p><b>安全（产品铁律）</b>：本视图<b>绝不</b>包含渠道 key / access_token / refresh_token / account_id
 * 等任何凭证明文——仅暴露用量结果与刷新发生标记。仅 AdminAuth 可达（控制器类级 RBAC 拦截）。</p>
 *
 * @param usage          上游 wham 用量数据 JSON 原文（原样透传，非 null）
 * @param tokenRefreshed 本次查询是否触发了 OAuth 凭证自动刷新回写（上游 401/403 → 刷新重试成功）
 */
public record CodexUsageView(String usage, boolean tokenRefreshed) {

    /**
     * 由领域用量值对象装配视图（接口层视图裁剪：只取用量原文 + 刷新标记，不含凭证）。
     *
     * @param usage 领域用量值对象
     * @return 管理视图
     */
    public static CodexUsageView from(CodexUsage usage) {
        return new CodexUsageView(usage.rawPayload(), usage.tokenRefreshed());
    }
}
