package com.nexa.interfaces.api.deployment;

import com.nexa.application.deployment.GetIntegrationStatusUseCase;
import com.nexa.application.deployment.TestConnectionUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.api.deployment.dto.ConnectionTestVO;
import com.nexa.interfaces.api.deployment.dto.IntegrationStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * io.net 集成开关与连接控制器（AdminAuth 端点，接口层，F-3039~F-3040）。
 *
 * <p>承载部署管理「集成开关与连接」端点（对齐 API-ENDPOINTS §10.1）：
 * <ul>
 *   <li>{@code GET  /api/deployments/settings} io.net 集成设置查询（F-3039）</li>
 *   <li>{@code POST /api/deployments/test}     io.net 连接测试（F-3040）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译。集成状态/连接测试结果在领域聚合派生，上游交互在 infra
 * （backend-engineer §2.1）。<b>请求/响应均不含 api_key 明文回显</b>（test 入参 api_key 仅透传 infra 用于
 * 上游鉴权，出参绝不带回——产品铁律：密钥不下发）。参数/集成错误由 {@code DeploymentExceptionHandler}
 * 统一翻译（key 缺失/无效→502）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/deployments/*} = AdminAuth；本切片尚未落地
 * AdminAuth 过滤器，SecurityConfig 当前对非公开端点统一 {@code authenticated()}（默认 401，不裸奔）。</p>
 */
@RestController
@RequestMapping("/api/deployments")
public class DeploymentIntegrationController {

    private final GetIntegrationStatusUseCase getIntegrationStatusUseCase;
    private final TestConnectionUseCase testConnectionUseCase;

    /**
     * @param getIntegrationStatusUseCase 集成设置查询用例（F-3039）
     * @param testConnectionUseCase       连接测试用例（F-3040）
     */
    public DeploymentIntegrationController(GetIntegrationStatusUseCase getIntegrationStatusUseCase,
                                           TestConnectionUseCase testConnectionUseCase) {
        this.getIntegrationStatusUseCase = getIntegrationStatusUseCase;
        this.testConnectionUseCase = testConnectionUseCase;
    }

    /**
     * io.net 集成设置查询（F-3039，{@code GET /api/deployments/settings}）。
     *
     * <p>无上游调用，纯反映本地配置。</p>
     *
     * @return 成功信封，data = {@code { provider, enabled, configured, can_connect }}
     */
    @GetMapping("/settings")
    public ApiResponse<IntegrationStatusVO> settings() {
        return ApiResponse.okData(IntegrationStatusVO.from(getIntegrationStatusUseCase.get()));
    }

    /**
     * io.net 连接测试（F-3040，{@code POST /api/deployments/test}）。
     *
     * <p>请求体 {@code api_key} 可选（为空回退已配置 stored key）；两者皆空→502「api_key is required」、
     * key 无效→502 上游 APIError（由 infra 抛、handler 翻译）。出参不回显 api_key。</p>
     *
     * @param request 请求体（可空，含可选 api_key）
     * @return 成功信封，data = {@code { hardware_count, total_available }}
     */
    @PostMapping("/test")
    public ApiResponse<ConnectionTestVO> test(@RequestBody(required = false) Map<String, Object> request) {
        // 从请求体取可选 api_key（透传 infra 用于本次连接鉴权，不回显）。
        String apiKey = request == null ? null : asString(request.get("api_key"));
        return ApiResponse.okData(ConnectionTestVO.from(testConnectionUseCase.test(apiKey)));
    }

    /**
     * 安全提取字符串值（非字符串/缺失→null），用于读取请求体可选字段。
     *
     * @param o 原始值
     * @return 字符串或 null
     */
    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
