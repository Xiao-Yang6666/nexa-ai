package com.nexa.application.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.domain.relay.service.KeyLimitChecker;
import com.nexa.domain.relay.vo.ProtocolFormat;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * key 级减法校验应用服务（RL-7 第③步接线点，REQ-06）。
 *
 * <p>转发主干（{@code RelayForwardUseCase.forward} 第③步）的<b>唯一对外入口</b>：输入当前 token + 平台
 * 公开名 A + 入站协议 inFmt，按 key 的减法约束放行或拒绝。应用层薄壳——只负责「按 tokenId 取聚合 +
 * 解析 {@code model_limits}/{@code endpoint_limits} JSON 串为集合」两件 I/O/序列化边界工作，规则判定全部
 * 下沉到纯领域服务 {@link KeyLimitChecker}（DDD：domain 零框架依赖，application 编排跨域端口）。</p>
 *
 * <p>领域规则来源：prd-model ML-8（模型权限全开，key 级限定为可选减法约束，<b>默认全开</b>）+
 * BILLING-MODEL-ARCHITECTURE §4.2（端点校验在 L1 前、模型校验对象 = A 在 L1 后 L2 前）。
 * 默认全开兜底分两层：① token 未注入（{@code tokenId == null}，鉴权未接线）即无约束放行；
 * ② token 存在但未启用对应开关即放行。</p>
 *
 * <p>不吞错：拒绝由 {@link KeyLimitChecker} 抛领域异常（{@code MODEL_NOT_ALLOWED} /
 * {@code ENDPOINT_NOT_ALLOWED}，均 403），由 {@code RelayExceptionHandler} 翻译；message 不含 token key。
 * JSON 解析失败按「该约束不可解析 → 视为未配 → 放行」处理（减法约束坏数据不应误伤合法请求，且无法
 * 据坏数据收窄；与「默认全开」一致），不抛异常阻断转发。</p>
 */
@Service
public class KeyLimitGuard {

    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    public KeyLimitGuard(TokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 key 级减法校验（主干 forward 第③步调用点）。
     *
     * <p>顺序：先端点维（ML-8 §3 端点在 L1 前）再模型维（校验对象 = A）。两者均默认全开：未启用开关、
     * 或无 token 上下文（{@code tokenId == null}）即放行。命中拒绝抛 403 领域异常。</p>
     *
     * @param tokenId              当前调用 token id（可空——鉴权未接线时为 null，按无约束放行）
     * @param resolvedPublicModelA 两层映射 L1 后平台公开名 A（模型维校验对象，必非空）
     * @param inboundFormat        本次请求入站协议 inFmt（RL-2 分发产出，端点维校验对象）
     * @throws com.nexa.domain.relay.exception.ModelMappingException     A 不在 key 模型允许集（启用时，403）
     * @throws com.nexa.domain.relay.exception.EndpointNotAllowedException inFmt 不在 key 端点允许集（启用时，403）
     */
    public void check(Long tokenId, String resolvedPublicModelA, ProtocolFormat inboundFormat) {
        if (tokenId == null) {
            return; // 默认全开：无 token 上下文（鉴权未接线）即无减法约束。
        }
        Optional<Token> maybeToken = tokenRepository.findById(tokenId);
        if (maybeToken.isEmpty()) {
            return; // token 不存在（已删/已撤）——本服务只管减法约束，存在性/有效性由鉴权链负责，不在此阻断。
        }
        Token token = maybeToken.get();

        // ① 端点维（ML-8 §3：TokenAuth 后、L1 前）。
        KeyLimitChecker.checkEndpoint(
                token.endpointLimitsEnabled(),
                parseLimitSet(token.endpointLimits()),
                inboundFormat);

        // ② 模型维（校验对象 = A，L1 之后 L2 之前）。
        KeyLimitChecker.checkModel(
                token.modelLimitsEnabled(),
                parseLimitSet(token.modelLimits()),
                resolvedPublicModelA);
    }

    /**
     * 解析 key 减法约束 JSON 串为允许集（边界职责，容多种历史编码，坏数据回退空集）。
     *
     * <p>兼容三种编码：① JSON 数组 {@code ["openai","claude"]}（端点集，DATA-MODEL Token 扩展）；
     * ② JSON 布尔表 {@code {"gpt-4":true,"o1":false}}（模型集，{@code GetModelLimitsMap} 口径，仅收 value 为 true 的键）；
     * ③ 旧逗号分隔串 {@code "gpt-4,o1"}（new-api 兼容回退）。空串/空白 → 空集（启用且空集按减法语义全拒，
     * 见 {@link KeyLimitChecker}）。无法解析的 JSON 异常 → 空集 + 不抛（坏数据不误伤，与默认全开一致）。</p>
     *
     * @param raw {@code model_limits}/{@code endpoint_limits} 原始串（可空）
     * @return 允许项集合（非 null；保序便于可观测）
     */
    private Set<String> parseLimitSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        String trimmed = raw.trim();
        Set<String> result = new LinkedHashSet<>();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.isArray()) {
                    for (JsonNode el : node) {
                        addIfText(result, el);
                    }
                } else if (node.isObject()) {
                    node.fields().forEachRemaining(e -> {
                        // 布尔表口径：仅 value 为 true（或缺省真值）的键计入允许集。
                        if (e.getValue() == null || !e.getValue().isBoolean() || e.getValue().asBoolean()) {
                            addTrimmed(result, e.getKey());
                        }
                    });
                }
                return result;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // 坏 JSON：回退空集（默认全开语义下不据坏数据收窄/误伤合法请求）。
                return Collections.emptySet();
            }
        }
        // 旧逗号分隔串回退。
        for (String part : trimmed.split(",")) {
            addTrimmed(result, part);
        }
        return result;
    }

    private static void addIfText(Set<String> set, JsonNode el) {
        if (el != null && el.isTextual()) {
            addTrimmed(set, el.asText());
        }
    }

    private static void addTrimmed(Set<String> set, String value) {
        if (value != null) {
            String v = value.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
    }
}
