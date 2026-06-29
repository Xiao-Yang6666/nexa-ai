package com.nexa.domain.relay.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 模型映射异常（404 / 拒绝）。
 *
 * <p>RL-7 两层映射执行中触发：
 * <ul>
 *   <li>映射成环 / 超最大跳数（FL-model 内核环检测）；</li>
 *   <li>A 在 L2 查不到底仓且不是直通模型（"那是客户的事"自然 404，ADR-COMPAT-06）；</li>
 *   <li>key 级减法约束 A ∉ ModelLimits 命中拒绝（开启时）。</li>
 * </ul>
 * </p>
 */
public class ModelMappingException extends HttpAwareDomainException {

    public ModelMappingException(String code, int httpStatus, String message) {
        super(code, httpStatus, message);
    }

    /** 映射成环（环检测拒绝态）。 */
    public static ModelMappingException cycleDetected(String startName) {
        return new ModelMappingException("MAPPING_CYCLE", 400,
                "model mapping cycle detected starting from: " + startName);
    }

    /** L2 查不到底仓（404）。 */
    public static ModelMappingException upstreamMissing(String publicName) {
        return new ModelMappingException("UPSTREAM_MODEL_MISSING", 404,
                "no upstream mapping for public model: " + publicName);
    }

    /** A 不在 key 的 ModelLimits 内（403）。 */
    public static ModelMappingException notAllowedByKey(String publicName) {
        return new ModelMappingException("MODEL_NOT_ALLOWED", 403,
                "model not allowed by key model_limits: " + publicName);
    }
}
