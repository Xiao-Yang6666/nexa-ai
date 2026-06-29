package com.nexa.domain.playground.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * Playground 禁用 access token 异常（F-4038 关键安全闸 → 403 ErrorCodeAccessDenied）。
 *
 * <p>领域规则来源：API-ENDPOINTS §13 F-4038「{@code use_access_token}→{@code ErrorCodeAccessDenied}
 * 『暂不支持使用 access token』」+ prd Playground「禁用 access token 是关键安全闸（防止 Playground
 * 临时令牌滥用 API key 权限）」。</p>
 *
 * <p>为何是安全闸而非便利限制：Playground 以 {@code tempToken{playground-<group>}} 走 relay，按用户
 * <b>实际额度</b>计费。若允许 access token（API key）调用，等于让站内试用通道承载了完整 API key 权限面，
 * 扩大了凭据滥用与额度旁路风险。因此只放行 <b>session</b>（登录会话）凭据，{@code Authorization: Bearer}
 * 形式的 access token 一律拒绝。</p>
 */
public final class PlaygroundAccessDeniedException extends HttpAwareDomainException {

    /** 稳定业务错误码（对齐现网 {@code ErrorCodeAccessDenied} 语义）。 */
    public static final String CODE = "ACCESS_DENIED";

    /** 客户可见提示（中文，对齐契约文案）。 */
    public static final String MESSAGE = "暂不支持使用 access token";

    /**
     * 构造禁用 access token 异常（→403）。
     */
    public PlaygroundAccessDeniedException() {
        super(CODE, 403, MESSAGE);
    }
}
