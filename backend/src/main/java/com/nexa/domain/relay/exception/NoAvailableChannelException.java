package com.nexa.domain.relay.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 无可用渠道异常（RL-1 §4 / RL-7 第④步「无可用渠道→上抛错误」，503）。
 *
 * <p>选渠（按 {@code (Group × B)} 查 Ability 加权随机 + CH-5 容灾兜底）耗尽仍无满足渠道时抛出。
 * 对齐 prd-relay RL-1 分支表「无可用渠道态」。message 不含上游凭证/渠道敏感信息，安全返客户。</p>
 *
 * <p>本期（REQ-02 骨架）由最小选渠占位抛出；REQ-03 接入完整 {@code ResolveChannelRouteUseCase}
 * （亲和 + 跨组重试）后由其在全组耗尽时抛出。</p>
 */
public class NoAvailableChannelException extends HttpAwareDomainException {

    public NoAvailableChannelException(String message) {
        super("NO_AVAILABLE_CHANNEL", 503, message);
    }
}
