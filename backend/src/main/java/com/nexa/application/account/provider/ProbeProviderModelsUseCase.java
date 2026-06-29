package com.nexa.application.account.provider;

import com.nexa.application.account.provider.port.ModelRegistryPort;
import com.nexa.application.account.provider.port.ProviderModelProbePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 探测上游模型列表用例（账号域，新建/编辑表单"获取模型列表"按钮调用）。
 *
 * <p>薄编排：把表单填的 platform/baseUrl/apiKey 透传给探测端口，端口按 platform 选协议
 * （OpenAI 兼容 / Anthropic / Gemini）发上游 HTTP 拉模型 ID 返回。<b>探测本身不依赖账号 id</b>
 * ——新建场景账号尚未保存即可探测。</p>
 *
 * <p><b>探测即自动入库（用户拍板方案 1）</b>：探测成功后，把拉到的模型名经
 * {@link ModelRegistryPort} 幂等登记进平台「模型管理」（model 上下文 ModelMeta 元数据表）——
 * 已存在的跳过、仅新建缺失的，新建采用本地自建语义（sync_official=0）。这样账号配好点「获取
 * 模型列表」，模型自动出现在模型管理 / 模型广场，无需手动二次录入。</p>
 *
 * <p><b>容错</b>：自动入库是探测的「附加副作用」，不是探测成功的前提。入库失败（如 model 域
 * 写库异常）只记日志、不冒泡——前端仍拿到探测结果正常勾选。探测本身失败才向上抛
 * （{@code ProviderProbeException}），由接口层翻译为 HTTP 错误。</p>
 */
@Service
public class ProbeProviderModelsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProbeProviderModelsUseCase.class);

    private final ProviderModelProbePort probePort;
    private final ModelRegistryPort modelRegistryPort;

    /**
     * @param probePort         上游模型探测端口
     * @param modelRegistryPort 模型登记端口（探测即自动入库到模型管理）
     */
    public ProbeProviderModelsUseCase(ProviderModelProbePort probePort,
                                      ModelRegistryPort modelRegistryPort) {
        this.probePort = probePort;
        this.modelRegistryPort = modelRegistryPort;
    }

    /**
     * 按表单参数探测上游模型列表，并把结果自动登记进模型管理（幂等）。
     *
     * @param platform 供应商平台
     * @param baseUrl  Base URL（可空）
     * @param apiKey   API Key（必填）
     * @return 上游模型 ID 列表（探测原始结果，供前端勾选）
     */
    public List<String> probe(String platform, String baseUrl, String apiKey) {
        List<String> models = probePort.fetchModels(platform, baseUrl, apiKey);

        // 探测即自动入库（方案 1）：容错——入库失败不影响探测结果返回。
        try {
            int created = modelRegistryPort.registerModelsIfAbsent(models);
            if (created > 0) {
                log.info("探测模型自动入库：platform={}, 新建 {} 个模型到模型管理", platform, created);
            }
        } catch (RuntimeException e) {
            // 入库是附加副作用，失败仅记日志，不冒泡——前端仍正常拿到探测结果勾选。
            log.warn("探测模型自动入库失败（不影响探测结果返回）：platform={}, err={}",
                    platform, e.getMessage());
        }

        return models;
    }
}
