package com.nexa.domain.relay.exception;

import com.nexa.common.kernel.HttpAwareDomainException;

/**
 * 模型组访问拒绝异常（私有模型组未授权访问，403，REQ-05 灵活模型组访问闸门）。
 *
 * <p>领域规则来源：灵活模型组管理——{@code access_policy=PRIVATE} 的模型组只对显式授权（用户级/令牌级）
 * 的调用方可见可用。当 token 使用的分组 code 命中一个私有模型组、但该 token 及其归属用户均无访问授权时
 * 抛出，闸门挡在选渠/计费之前（不放行到上游、不扣费）。</p>
 *
 * <p>语义为<b>权限闸门</b>（区别于 key 减法约束的自我收窄）：私有组是加法授权模型，未授权即拒绝。
 * message 仅含分组 code（非敏感），绝不含 token key / 上游凭证。</p>
 */
public class ModelGroupAccessDeniedException extends HttpAwareDomainException {

    /**
     * @param groupCode 被拒的私有模型组分组 code（非敏感）
     */
    public ModelGroupAccessDeniedException(String groupCode) {
        super("MODEL_GROUP_ACCESS_DENIED", 403,
                "access denied to private model group: " + groupCode);
    }
}
