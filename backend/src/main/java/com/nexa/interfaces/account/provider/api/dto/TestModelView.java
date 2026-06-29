package com.nexa.interfaces.account.provider.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.account.provider.port.ProviderModelTestPort.ProviderModelTestResult;

/**
 * 模型连通性测试结果视图（账号域，回前端展示）。
 *
 * <p>成功才返回此视图（ok=true）；失败由 {@code ProviderProbeException} → 502 错误信封承载，
 * 前端按错误信封展示。绝不含任何凭证信息。</p>
 *
 * @param ok        是否成功（恒 true；失败走错误信封）
 * @param latencyMs 上游往返耗时（毫秒）
 * @param reply     上游回复正文片段（已截断，可空）
 */
public record TestModelView(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("reply") String reply) {

    /** 由领域测试结果裁剪为视图。 */
    public static TestModelView from(ProviderModelTestResult result) {
        return new TestModelView(true, result.latencyMs(), result.reply());
    }
}
