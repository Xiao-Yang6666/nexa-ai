package com.nexa.domain.relay.exception;

import com.nexa.domain.kernel.HttpAwareDomainException;

/**
 * 协议适配器缺失 / 能力不支持异常（RL-4 ad_find-否、RL-6 注册表未命中、RL-2 RelayNotImplemented）。
 *
 * <p>three-class:
 * <ul>
 *   <li>渠道类型对应 adapter 缺失（RL-4 ad_miss）；</li>
 *   <li>协议注册表未命中（RL-6 cp_legacy 回落，但若回落也不可行则上抛）；</li>
 *   <li>RL-2 RelayNotImplemented（{@code /v1/images/variations}）。</li>
 * </ul>
 * </p>
 */
public class RelayNotImplementedException extends HttpAwareDomainException {

    public RelayNotImplementedException(String message) {
        super("RELAY_NOT_IMPLEMENTED", 501, message);
    }
}
