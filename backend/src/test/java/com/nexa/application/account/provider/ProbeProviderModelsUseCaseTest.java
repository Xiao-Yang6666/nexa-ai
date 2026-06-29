package com.nexa.application.account.provider;

import com.nexa.application.account.provider.port.ModelRegistryPort;
import com.nexa.application.account.provider.port.ProviderModelProbePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 探测用例「探测即自动入库」单测（方案 1）。
 *
 * <p>用桩端口验证编排：探测结果原样返回给前端勾选，同时把模型名透传给登记端口入库；
 * 登记失败容错（不冒泡），探测本身失败才上抛。</p>
 */
class ProbeProviderModelsUseCaseTest {

    @Test
    void probe_returnsModelsAndRegistersThemToModelManagement() {
        // 探测端口桩：返回三个模型。
        ProviderModelProbePort probe = (platform, baseUrl, apiKey) ->
                List.of("gpt-4o", "gpt-4o-mini", "o1");
        // 登记端口桩：记录收到的模型名，返回新建 2 个。
        AtomicReference<List<String>> registered = new AtomicReference<>(new ArrayList<>());
        ModelRegistryPort registry = names -> {
            registered.set(List.copyOf(names));
            return 2;
        };

        ProbeProviderModelsUseCase useCase = new ProbeProviderModelsUseCase(probe, registry);
        List<String> result = useCase.probe("openai", null, "sk-test");

        // 探测结果原样返回（供前端勾选）。
        assertThat(result).containsExactly("gpt-4o", "gpt-4o-mini", "o1");
        // 探测到的模型被透传给登记端口入库。
        assertThat(registered.get()).containsExactly("gpt-4o", "gpt-4o-mini", "o1");
    }

    @Test
    void probe_stillReturnsModels_whenRegistrationThrows() {
        ProviderModelProbePort probe = (platform, baseUrl, apiKey) -> List.of("claude-3-5-sonnet");
        // 登记端口抛异常——模拟 model 域写库失败。
        ModelRegistryPort failingRegistry = names -> {
            throw new RuntimeException("model 域写库失败");
        };

        ProbeProviderModelsUseCase useCase = new ProbeProviderModelsUseCase(probe, failingRegistry);

        // 入库失败是附加副作用，不应冒泡——前端仍正常拿到探测结果。
        List<String> result = useCase.probe("anthropic", null, "sk-test");
        assertThat(result).containsExactly("claude-3-5-sonnet");
    }

    @Test
    void probe_propagatesProbeFailure() {
        // 探测本身失败必须上抛（接口层翻译为 HTTP 错误）。
        ProviderModelProbePort failingProbe = (platform, baseUrl, apiKey) -> {
            throw new ProviderModelProbePort.ProviderProbeException("上游 401");
        };
        ModelRegistryPort registry = names -> 0;

        ProbeProviderModelsUseCase useCase = new ProbeProviderModelsUseCase(failingProbe, registry);

        try {
            useCase.probe("openai", null, "bad-key");
            assertThat(false).as("应抛 ProviderProbeException").isTrue();
        } catch (ProviderModelProbePort.ProviderProbeException expected) {
            assertThat(expected.getMessage()).contains("上游 401");
        }
    }
}
