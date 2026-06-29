package com.nexa.routing.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * auto 分组未启用异常（F-2035，PRD CH-5 节点 ag_disable）。
 *
 * <p>领域规则来源：prd-channel CH-5「{@code GetAutoGroups()} 为空返回『auto groups is not enabled』」。
 * 令牌 {@code Group=auto} 发起请求但系统未配置任何 auto 分组时抛出，接口层翻译为 400（配置缺失，
 * 客户端可见但属请求语义不可满足）。</p>
 */
public class AutoGroupsNotEnabledException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "AUTO_GROUPS_NOT_ENABLED";

    /** 现网兼容文案（对齐 prd-channel CH-5）。 */
    public static final String DEFAULT_MESSAGE = "auto groups is not enabled";

    /** 以现网兼容文案构造。 */
    public AutoGroupsNotEnabledException() {
        super(CODE, DEFAULT_MESSAGE);
    }
}
