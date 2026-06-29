package com.nexa.interfaces.relay.api.dto;

import com.nexa.domain.relay.model.RelayLog;

/**
 * 用户视图日志 DTO（序列化层裁剪，**B / 成本 / 利润绝不出现**，COMPAT-LAYER-DATA-OBJECTS §3.1）。
 *
 * <p>可见性铁律（ADR-COMPAT-05 序列化层闸）：UserAuth 用量/日志接口只返 C/A + quota_sell（售价客户可见），
 * **不含** {@code actual_upstream_model}(B)、{@code quota_cost}、{@code quota_profit}、{@code channel}。
 * 由 {@link #from(RelayLog)} 静态裁剪，不靠前端隐藏。</p>
 *
 * @param requestedModel      C 客户输入名（可见）
 * @param resolvedPublicModel A 平台公开名（可见）
 * @param promptTokens        入参 token
 * @param completionTokens    出参 token
 * @param quotaSell           售价（客户可见）
 * @param createdAt           epoch 秒
 */
public record UserLogView(
        String requestedModel,
        String resolvedPublicModel,
        int promptTokens,
        int completionTokens,
        int quotaSell,
        long createdAt
) {

    /** 从领域日志裁剪为用户视图（B/成本/利润/渠道全部丢弃）。 */
    public static UserLogView from(RelayLog log) {
        return new UserLogView(
                log.requestedModel(),
                log.resolvedPublicModel(),
                log.promptTokens(),
                log.completionTokens(),
                log.quotaSell(),
                log.createdAt());
    }
}
