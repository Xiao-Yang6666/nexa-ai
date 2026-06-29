package com.nexa.application.account.provider.port;

import java.util.List;

/**
 * 上游模型探测端口（账号域，AdminAuth 新建/编辑时调上游 /v1/models 拉模型 ID）。
 *
 * <p>领域只持端口（防腐层），适配器在 infrastructure 层用 RestClient 真发上游 HTTP。
 * 失败统一抛 {@link ProviderProbeException}（接口层映射为 502/400）。</p>
 */
public interface ProviderModelProbePort {

    /**
     * 探测上游支持的模型 ID 列表。
     *
     * @param platform 供应商平台标识（openai/anthropic/azure/google/deepseek/...，决定请求头与端点规则）
     * @param baseUrl  上游 Base URL（可空 → 按 platform 回落官方默认；无默认且为空时抛异常）
     * @param apiKey   上游 API Key（必填，鉴权用）
     * @return 模型 ID 列表（保序去重）
     * @throws ProviderProbeException 入参非法、网络失败、上游非 2xx、响应解析失败
     */
    List<String> fetchModels(String platform, String baseUrl, String apiKey);

    /** 探测异常（统一上抛，接口层翻译为 HTTP 错误）。 */
    class ProviderProbeException extends RuntimeException {
        public ProviderProbeException(String message) {
            super(message);
        }
        public ProviderProbeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
