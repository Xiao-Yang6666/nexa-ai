package com.nexa.ops.interfaces.api;

import com.nexa.ops.domain.exception.DomainException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 运营与运维接口层异常处理（协议翻译：ops 领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link DomainException} 子类，自携稳定 code + 建议 httpStatus），
 * 接口层在此集中翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}），用例/控制器
 * 因此不写 try/catch 模板代码（backend-engineer §3.2）。</p>
 *
 * <p>作用域：用 {@code basePackages} 限定仅作用于 {@code com.nexa.ops.interfaces}，不影响其他 BC；
 * 优先级高于全站兜底 {@code SecurityExceptionHandler}（{@link Ordered#LOWEST_PRECEDENCE}），就近匹配。</p>
 *
 * <p>状态码映射统一读 {@link DomainException#httpStatus()}（各子类自声明）：
 * <ul>
 *   <li>InvalidSetupRequest / InvalidOptionValue / InvalidMaintenanceRequest → 400</li>
 *   <li>PaymentCompliance → 400（未确认）或 403（非 dashboard 会话）</li>
 *   <li>SystemAlreadyInitialized → 409</li>
 * </ul>
 * 鉴权类异常（401/403 由 RequireRole/CurrentActor 抛出的 Authentication/AccessDenied）由全站
 * {@code SecurityExceptionHandler} 兜底，本处理器不重复。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.nexa.ops.interfaces")
public class OpsExceptionHandler {

    /**
     * ops 领域异常统一翻译（按异常自声明的 httpStatus + message）。
     *
     * @param e 领域异常
     * @return 对应状态码的错误信封
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException e) {
        HttpStatus status = HttpStatus.resolve(e.httpStatus());
        if (status == null) {
            // 防御：未知 httpStatus 归 500（不静默吞，按服务端错误暴露）。
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
    }
}
