package com.nexa.interfaces.billing.api;

import com.nexa.common.kernel.DomainException;
import com.nexa.domain.billing.exception.RedemptionAlreadyUsedException;
import com.nexa.domain.billing.exception.RedemptionExpiredException;
import com.nexa.domain.billing.exception.RedemptionInvalidException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 计费与钱包接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link DomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适 HTTP 状态码，
 * 用例/控制器因此不写 try/catch 模板（backend-engineer §3.2）。仅对计费域控制器包生效
 * （{@code basePackages}），不影响账号/渠道/部署模块各自的异常处理器。</p>
 *
 * <p>状态码映射（计费域领域异常多为客户端可纠正的业务校验失败，统一 400，对齐 openapi
 * 计费端点的 {@code BadRequestError}）：
 * <ul>
 *   <li>{@link RedemptionInvalidException} → 400（码不存在/格式错，BL-4 rd_find-否）</li>
 *   <li>{@link RedemptionAlreadyUsedException} → 400（已使用/已禁用，BL-4 rd_used-是）</li>
 *   <li>{@link RedemptionExpiredException} → 400（已过期，BL-4 rd_exp-是）</li>
 *   <li>其余 {@link DomainException}（含参数非法/余额不足/订阅守卫等）→ 400（透传 code 不在 body，
 *       只回 message，避免泄露内部错误码语义）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul></p>
 */
@RestControllerAdvice(basePackages = "com.nexa.interfaces.billing.api")
public class BillingExceptionHandler {

    /**
     * 计费域领域异常统一 → 400（业务校验失败，客户端可纠正）。
     *
     * <p>计费域的领域异常语义上都是「请求合法但违反业务规则」（码无效/已用/过期、金额非法、
     * 余额不足、订阅额度耗尽等），对齐 openapi 计费端点仅声明 400 的契约，统一映射为 400。
     * message 透传领域异常的可读描述（不含敏感值），错误码 {@code code()} 不下发 body
     * （前端按 message 提示即可，不暴露内部码语义）。</p>
     *
     * @param e 计费域领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }
}
