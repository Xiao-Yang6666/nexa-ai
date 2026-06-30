package com.nexa.domain.ops.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 系统已初始化，拒绝重复初始化（F-4016，幂等护栏）。
 *
 * <p>领域规则来源：API-ENDPOINTS §9.1 POST /api/setup「已初始化→『系统已经初始化完成』」，
 * 幂等键 {@code constant.Setup}。已存在初始化标记时再次提交即抛本异常 → 409 Conflict
 * （重复的不可重做动作，语义上是状态冲突而非客户端入参错误）。</p>
 */
public class SystemAlreadyInitializedException extends HttpAwareDomainException {

    /** 构造重复初始化异常。 */
    public SystemAlreadyInitializedException() {
        super("OPS_ALREADY_INITIALIZED", 409, "系统已经初始化完成");
    }
}
