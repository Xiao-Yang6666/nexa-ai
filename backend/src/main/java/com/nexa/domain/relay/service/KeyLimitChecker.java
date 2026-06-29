package com.nexa.domain.relay.service;

import com.nexa.domain.relay.exception.EndpointNotAllowedException;
import com.nexa.domain.relay.exception.ModelMappingException;
import com.nexa.domain.relay.vo.ProtocolFormat;

import java.util.Set;

/**
 * key 级减法校验领域服务（RL-7 第③步 / ML-8 §3，纯函数零框架依赖）。
 *
 * <p>领域规则来源：prd-model ML-8（模型权限全开，key 级模型/端点限定为<b>可选减法约束</b>，默认全开）+
 * BILLING-MODEL-ARCHITECTURE §4.2（端点校验在 TokenAuth 后、L1 前；模型校验对象 = A，L1 之后 L2 之前）。
 * 两条减法约束彼此独立：
 * <ul>
 *   <li><b>ModelLimits 对 A</b>：{@code modelLimitsEnabled=true} 时，平台公开名 A 必须 ∈ 允许集，
 *       否则拒绝（{@link ModelMappingException#notAllowedByKey}）；{@code false}（默认）全开放行。</li>
 *   <li><b>EndpointLimits 对 inFmt</b>：{@code endpointLimitsEnabled=true} 时，入站协议 inFmt 必须 ∈
 *       允许集，否则拒绝（{@link EndpointNotAllowedException}）；{@code false}（默认）全开放行。</li>
 * </ul></p>
 *
 * <p><b>默认全开（核心语义）</b>：未启用对应开关即放行——key 未配限制等于不限。启用后为纯减法自我约束
 * （无加法授权路径，ML-8 天然安全），命中允许集外即拒。允许集已由调用方（应用层）从 token 的
 * {@code model_limits}/{@code endpoint_limits} 串解析为 {@code Set<String>}，本服务只做集合判定，
 * 不碰持久化/JSON（DDD：解析属边界职责，规则属领域）。</p>
 *
 * <p><b>边界约定</b>：启用且允许集为空视为「收窄到空」——按减法语义一律拒绝（A/inFmt 必不在空集）。
 * 该退化配置由 token 编辑期约束（端点开关随内容派生，空内容不启用），本服务只忠实执行集合判定。</p>
 *
 * <p>不吞错：拒绝即抛领域异常（携稳定错误码 + 403），由接口层 {@code RelayExceptionHandler} 翻译；
 * message 仅含模型名 A / 协议线值（非敏感），绝不含 token key / 上游凭证。</p>
 */
public final class KeyLimitChecker {

    private KeyLimitChecker() {
    }

    /**
     * 校验 A 是否被 key 的模型级减法约束放行（RL-7 ③ 模型维，校验对象 = A，L1 之后 L2 之前）。
     *
     * <p>默认全开：{@code enabled=false} 直接放行。启用时 A ∉ {@code allowedModels} 抛
     * {@link ModelMappingException#notAllowedByKey}（403，自我约束非越权）。</p>
     *
     * @param enabled       是否启用模型限制（{@code Token.modelLimitsEnabled}）
     * @param allowedModels 允许的平台公开名 A 集合（由 {@code Token.modelLimits} 解析，可空/可空集）
     * @param resolvedPublicModelA 两层映射 L1 后的平台公开名 A（定价键，校验对象）
     * @throws ModelMappingException A 不在允许集（启用时）
     */
    public static void checkModel(boolean enabled, Set<String> allowedModels, String resolvedPublicModelA) {
        if (!enabled) {
            return; // 默认全开：未启用模型限制即放行。
        }
        if (allowedModels == null || !allowedModels.contains(resolvedPublicModelA)) {
            throw ModelMappingException.notAllowedByKey(resolvedPublicModelA);
        }
    }

    /**
     * 校验入站协议 inFmt 是否被 key 的端点级减法约束放行（ML-8 §3 端点维，TokenAuth 后、L1 前）。
     *
     * <p>默认全开：{@code enabled=false} 直接放行。启用时 inFmt 的线值 ∉ {@code allowedEndpoints} 抛
     * {@link EndpointNotAllowedException}（403，自我约束非越权）。比较以 {@link ProtocolFormat#wireValue()}
     * 线值（如 {@code "openai"}/{@code "claude"}）为准，与 {@code endpoint_limits} JSON 入站协议集同口径。</p>
     *
     * @param enabled          是否启用端点限制（{@code Token.endpointLimitsEnabled}）
     * @param allowedEndpoints 允许的入站协议线值集合（由 {@code Token.endpointLimits} 解析，可空/可空集）
     * @param inboundFormat    本次请求入站协议（RL-2 路径分发产出）
     * @throws EndpointNotAllowedException inFmt 不在允许集（启用时）
     */
    public static void checkEndpoint(boolean enabled, Set<String> allowedEndpoints, ProtocolFormat inboundFormat) {
        if (!enabled) {
            return; // 默认全开：未启用端点限制即放行。
        }
        String wire = inboundFormat == null ? null : inboundFormat.wireValue();
        if (allowedEndpoints == null || !allowedEndpoints.contains(wire)) {
            throw new EndpointNotAllowedException(wire);
        }
    }
}
