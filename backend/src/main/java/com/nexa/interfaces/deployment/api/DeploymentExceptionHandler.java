package com.nexa.interfaces.deployment.api;

import com.nexa.common.kernel.DomainException;

import com.nexa.domain.deployment.exception.DeploymentNameUnavailableException;
import com.nexa.domain.deployment.exception.InvalidDeploymentParameterException;
import com.nexa.domain.deployment.exception.IonetIntegrationException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 部署管理接口层异常处理（协议翻译：领域/集成异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码，
 * 用例/控制器因此不写 try/catch 模板代码（backend-engineer §3.2）。仅对部署模块两个控制器生效
 * （{@code assignableTypes}），不影响账号模块的 {@code GlobalExceptionHandler}。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidDeploymentParameterException} → 400（缺失/非法入参，契约固定文案透传）</li>
 *   <li>{@link IonetIntegrationException} → 502（上游 io.net 未配置/连接失败/APIError，5xx 语义）</li>
 *   <li>请求体不可读（非法 JSON）→ 400（对齐契约「请求体非法→绑定错误」）</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice(assignableTypes = {
        DeploymentMetadataController.class, ContainerController.class,
        DeploymentController.class, DeploymentIntegrationController.class})
public class DeploymentExceptionHandler {

    /**
     * 部署参数非法 → 400（缺失必填/越界，契约文案透传）。
     *
     * <p>文案在领域值对象内按契约固定（如「hardware_id parameter is required」「deployment ID is required」
     * 「deployment name cannot be empty」），这里原样透传 message（不含可枚举敏感信息）。</p>
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidDeploymentParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidDeploymentParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 部署名称不可用 → 409 Conflict（F-3046 重命名预检发现名称已占用）。
     *
     * <p>区别于 400（入参非法）与 502（上游故障）：请求合法、上游正常，但名称冲突无法完成，语义为冲突。
     * message 为契约固定文案「name is not available」。</p>
     *
     * @param e 名称不可用异常
     * @return 409 错误信封
     */
    @ExceptionHandler(DeploymentNameUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNameUnavailable(DeploymentNameUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * io.net 上游集成失败 → 502（未配置/连接失败/上游 APIError）。
     *
     * <p>message 透传上游 {@code APIError.Message}（空时已在 infra 回退稳定文案），不下发任何凭证
     * （敏感键剔除在 infra 层）。用 502 Bad Gateway 表达「本服务正常但上游故障」，区别于 4xx 客户端错误。</p>
     *
     * @param e 集成异常
     * @return 502 错误信封
     */
    @ExceptionHandler(IonetIntegrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegration(IonetIntegrationException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(e.getMessage()));
    }
}
