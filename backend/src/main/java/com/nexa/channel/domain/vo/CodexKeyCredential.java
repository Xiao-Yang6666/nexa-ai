package com.nexa.channel.domain.vo;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;

import java.util.Objects;

/**
 * Codex 渠道 OAuth 凭证值对象（不可变，F-4045 上游用量查询前置解析）。
 *
 * <p>领域规则来源：API-ENDPOINTS §5.8 / BACKLOG F-4045。Codex 渠道（type=54）的 key 不是普通
 * Bearer token，而是一段承载 OAuth 三要素的凭证：
 * <ul>
 *   <li>{@code accessToken}  —— 调上游 wham 用量接口的 access_token（必需，缺失拒绝）。</li>
 *   <li>{@code accountId}    —— Codex 账户标识 account_id（必需，缺失拒绝）。</li>
 *   <li>{@code refreshToken} —— 刷新令牌（可空；用于 401/403 时自动 RefreshCodexOAuthToken 重试）。</li>
 * </ul>
 * 本值对象按值相等、构造后不可变。它<b>仅在领域内表达「凭证已解析且必填项齐全」这一不变量</b>，
 * 不负责真实网络调用（那是 infra 的 {@link com.nexa.channel.application.port.ChannelProbeClient}）。</p>
 *
 * <p>凭证解析格式：Codex key 约定为 {@code access_token|account_id|refresh_token} 的竖线分隔串
 * （与 new-api Codex 渠道 key 存储语义对齐——多段凭证拼一串持久化）。refresh_token 段可省略。
 * 真实上游若改用 JSON 等其它编码，仅需替换 {@link #parse(String)} 的解析实现，领域不变量不变。</p>
 *
 * <p>安全：本值对象承载敏感凭证，{@link #toString()} 已脱敏（绝不打印 token 明文），
 * 不下发任何客户/管理视图（视图只暴露用量结果，不回显凭证）。</p>
 *
 * @param accessToken  上游 wham 用量查询 access_token（非空白）
 * @param accountId    Codex account_id（非空白）
 * @param refreshToken 刷新令牌（可空——无则 401/403 不可自动刷新重试）
 */
public record CodexKeyCredential(String accessToken, String accountId, String refreshToken) {

    /** Codex key 段分隔符（access_token|account_id|refresh_token）。 */
    public static final String SEGMENT_DELIMITER = "|";

    /**
     * 紧凑构造器：守护必填不变量（access_token / account_id 非空白）。
     *
     * @throws InvalidChannelParameterException access_token 或 account_id 缺失
     */
    public CodexKeyCredential {
        if (accessToken == null || accessToken.isBlank()) {
            // 对齐 API-ENDPOINTS §5.8「access_token 缺失→对应报错」。
            throw new InvalidChannelParameterException("codex channel access_token is missing");
        }
        if (accountId == null || accountId.isBlank()) {
            // 对齐 API-ENDPOINTS §5.8「account_id 缺失→对应报错」。
            throw new InvalidChannelParameterException("codex channel account_id is missing");
        }
        accessToken = accessToken.trim();
        accountId = accountId.trim();
        refreshToken = (refreshToken == null || refreshToken.isBlank()) ? null : refreshToken.trim();
    }

    /**
     * 从渠道 key 原文解析 Codex OAuth 凭证（F-4045）。
     *
     * <p>按 {@link #SEGMENT_DELIMITER} 竖线分隔取段：第 1 段 access_token、第 2 段 account_id、
     * 第 3 段（可选）refresh_token。段数不足以致 access_token/account_id 缺失时，由紧凑构造器
     * 抛 {@link InvalidChannelParameterException}（接口层映射 400）。</p>
     *
     * @param rawKey 渠道 key 原文（敏感）
     * @return 解析后的凭证值对象
     * @throws InvalidChannelParameterException key 为空或必填段缺失
     */
    public static CodexKeyCredential parse(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new InvalidChannelParameterException("codex channel key is empty");
        }
        // 用 -1 limit 保留尾部空段，便于精确表达「给了分隔符但段为空」=缺失。
        String[] segments = rawKey.split("\\" + SEGMENT_DELIMITER, -1);
        String access = segments.length > 0 ? segments[0] : null;
        String account = segments.length > 1 ? segments[1] : null;
        String refresh = segments.length > 2 ? segments[2] : null;
        return new CodexKeyCredential(access, account, refresh);
    }

    /** @return 是否具备 refresh_token（具备才能在上游 401/403 时自动刷新重试，F-4045 副作用） */
    public boolean canRefresh() {
        return refreshToken != null;
    }

    /**
     * 脱敏 toString，绝不回显 token 明文（安全默认）。
     *
     * @return 不含凭证明文的描述
     */
    @Override
    public String toString() {
        return "CodexKeyCredential{accountId=" + accountId
                + ", accessToken=***, refreshToken=" + (canRefresh() ? "***" : "<none>") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CodexKeyCredential other)) {
            return false;
        }
        return Objects.equals(accessToken, other.accessToken)
                && Objects.equals(accountId, other.accountId)
                && Objects.equals(refreshToken, other.refreshToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, accountId, refreshToken);
    }
}
