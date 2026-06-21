package com.nexa.relay.domain.service;

import java.util.Set;

/**
 * 上游错误重试策略领域服务（RL-3 ShouldRetryByStatusCode，纯函数零框架依赖）。
 *
 * <p>领域规则来源：prd-relay.md RL-3 + FC-086。
 * <ul>
 *   <li>可重试状态码 = 服务端临时故障（5xx 部分 + 408）；</li>
 *   <li>不可重试码 = 客户端明确错误 / 鉴权失败 / 额度不足等；</li>
 *   <li>SkipRetry 的场景（如 BL-2 额度不足 403）由调用方在 preConsume 阶段标记，不走本策略。</li>
 * </ul>
 * </p>
 */
public final class RetryPolicy {

    /** 默认最大重试次数（common.RetryTimes，含首次调用后的重试，可配置覆盖）。 */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** 可重试状态码集合（服务端临时故障 + 超时）。 */
    private static final Set<Integer> RETRYABLE_CODES = Set.of(
            408,  // Request Timeout
            429,  // Rate Limited (upstream)
            500,  // Internal Server Error
            502,  // Bad Gateway
            503,  // Service Unavailable
            504   // Gateway Timeout
    );

    /** 明确不可重试码（客户端错误，立即跳过）。 */
    private static final Set<Integer> NON_RETRYABLE_CODES = Set.of(
            400, 401, 403, 404, 405, 413, 415, 422
    );

    private RetryPolicy() {
    }

    /**
     * 判定上游状态码是否可重试（RL-3 ShouldRetryByStatusCode）。
     *
     * <p>逻辑：明确不可重试码 → false；可重试码集合 → true；其余（不认识的码）→ false（保守）。</p>
     *
     * @param statusCode 上游 HTTP 状态码
     * @return true = 可换渠道重试
     */
    public static boolean shouldRetry(int statusCode) {
        if (NON_RETRYABLE_CODES.contains(statusCode)) {
            return false;
        }
        return RETRYABLE_CODES.contains(statusCode);
    }

    /**
     * 判定渠道是否应自动禁用（RL-3 AutoBan=1 + 命中禁用条件）。
     *
     * <p>禁用条件：上游返回 401（凭证失效）或 403（被封禁）时自动禁用（高置信度），
     * 其余状态码不自动禁用（避免误伤 —— 如 429 是正常限流，不该禁用渠道）。
     * 仅 autoBan==1 的渠道才允许自动禁用。</p>
     *
     * @param autoBan    渠道 auto_ban 值（1=允许自动禁用，0=不允许）
     * @param statusCode 上游返回的 HTTP 状态码
     * @return true = 应自动禁用
     */
    public static boolean shouldAutoDisable(int autoBan, int statusCode) {
        if (autoBan != 1) {
            return false;
        }
        // 401/403 强烈暗示凭证或权限永久失效
        return statusCode == 401 || statusCode == 403;
    }
}
