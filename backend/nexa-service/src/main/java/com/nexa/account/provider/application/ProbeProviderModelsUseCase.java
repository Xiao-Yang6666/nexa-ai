package com.nexa.account.provider.application;

import com.nexa.account.provider.application.port.ProviderModelProbePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 探测上游模型列表用例（账号域，新建/编辑表单"获取模型列表"按钮调用）。
 *
 * <p>薄编排：把表单填的 platform/baseUrl/apiKey 透传给端口，端口按 platform 选协议（OpenAI 兼容
 * / Anthropic / Gemini）发上游 HTTP 拉模型 ID 返回。<b>不落库、不依赖账号 id</b>——新建场景账号
 * 尚未保存即可探测。</p>
 */
@Service
public class ProbeProviderModelsUseCase {

    private final ProviderModelProbePort probePort;

    public ProbeProviderModelsUseCase(ProviderModelProbePort probePort) {
        this.probePort = probePort;
    }

    /**
     * 按表单参数探测上游模型列表。
     *
     * @param platform 供应商平台
     * @param baseUrl  Base URL（可空）
     * @param apiKey   API Key（必填）
     * @return 上游模型 ID 列表
     */
    public List<String> probe(String platform, String baseUrl, String apiKey) {
        return probePort.fetchModels(platform, baseUrl, apiKey);
    }
}
