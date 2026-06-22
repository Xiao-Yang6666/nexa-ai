package com.nexa.channel.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 供应商成本配置不存在异常（接口层映射 404，F-6006）。
 *
 * <p>按 id 操作 ChannelModelCost 但目标缺失时抛出。</p>
 */
public class ChannelModelCostNotFoundException extends DomainException {

    /** @param id 缺失的成本行 id */
    public ChannelModelCostNotFoundException(long id) {
        super("CHANNEL_MODEL_COST_NOT_FOUND", "channel model cost not found: id=" + id);
    }
}
