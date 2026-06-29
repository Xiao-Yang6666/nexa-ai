package com.nexa.account.provider.application.port;

/**
 * 上游模型连通性测试端口（账号域，AdminAuth 在账号操作里点「测试」发一次真实 chat 调用）。
 *
 * <p>与 {@link ProviderModelProbePort}（拉 /models 列表）互补：本端口对指定 model 发一次
 * 非流式聊天补全，验证「这个账号 + 这个模型」真的能跑通——含鉴权、模型可用性、上游连通性。
 * 领域只持端口（防腐层），适配器在 infrastructure 层用 RestClient 真发上游 HTTP。</p>
 *
 * <p>失败统一抛 {@link ProviderModelProbePort.ProviderProbeException}（与探测复用同一异常族，
 * 接口层映射为 502）。message 绝不回显 apiKey。</p>
 */
public interface ProviderModelTestPort {

    /**
     * 对指定模型发一次非流式聊天补全测试。
     *
     * @param platform 供应商平台标识（决定协议与端点：OpenAI 兼容 / Anthropic / Gemini）
     * @param baseUrl  上游 Base URL（可空 → 按 platform 回落官方默认；无默认且为空时抛异常）
     * @param apiKey   上游 API Key（必填，鉴权用）
     * @param model    要测试的模型 ID（必填）
     * @param prompt   测试提示词（可空 → 用默认 "ping" 类短提示）
     * @return 测试结果（成功标志、耗时、回复片段）
     * @throws ProviderModelProbePort.ProviderProbeException 入参非法、网络失败、上游非 2xx、响应解析失败
     */
    ProviderModelTestResult testChat(String platform, String baseUrl, String apiKey,
                                     String model, String prompt);

    /**
     * 对指定模型发一次<b>流式</b>聊天补全测试，逐 token 增量回调。
     *
     * <p>用 {@code stream=true} 调上游，按 SSE 事件解析出增量文本片段（delta），每片回调
     * {@link TestStreamListener#onDelta(String)}；正常收束回调
     * {@link TestStreamListener#onComplete(ProviderModelTestResult)}（含总耗时 + 累计文本）。
     * 上游非 2xx / 网络失败 / 解析失败统一抛 {@link ProviderModelProbePort.ProviderProbeException}
     * ——仅在<b>尚未回调任何 delta</b> 前抛（接口层据此翻译为错误信封）；首片已发出后的中断由实现
     * 内部消化（onComplete 用已累计结果收束），不外抛。</p>
     *
     * @param platform 供应商平台标识
     * @param baseUrl  上游 Base URL（可空 → 回落默认）
     * @param apiKey   上游 API Key（必填）
     * @param model    要测试的模型 ID（必填）
     * @param prompt   测试提示词（可空 → 默认短提示）
     * @param listener 增量 + 收束回调
     * @throws ProviderModelProbePort.ProviderProbeException 首片前的入参非法 / 上游失败 / 解析失败
     */
    void testChatStream(String platform, String baseUrl, String apiKey,
                        String model, String prompt, TestStreamListener listener);

    /**
     * 流式测试监听器（增量 token + 收束）。
     */
    interface TestStreamListener {
        /**
         * 收到一片增量文本（已从 SSE 事件解析出的 delta；空串可忽略）。
         *
         * @param text 增量文本片段
         */
        void onDelta(String text);

        /**
         * 流正常收束。
         *
         * @param result 含总耗时 + 累计完整文本
         */
        void onComplete(ProviderModelTestResult result);
    }

    /**
     * 模型测试结果（接口层裁剪为视图回前端）。
     *
     * @param latencyMs 上游往返耗时（毫秒）
     * @param reply     上游回复正文片段（截断；可空）
     */
    record ProviderModelTestResult(long latencyMs, String reply) {
    }
}
