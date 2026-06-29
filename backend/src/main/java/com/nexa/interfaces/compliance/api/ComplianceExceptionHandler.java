package com.nexa.interfaces.compliance.api;

import com.nexa.domain.account.exception.UserNotFoundException;
import com.nexa.shared.web.ApiResponse;
import com.nexa.domain.compliance.exception.ConsentRequiredException;
import com.nexa.domain.compliance.exception.CrossBorderRoutingDeniedException;
import com.nexa.domain.compliance.exception.InvalidRetentionPolicyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 合规子域接口层全局异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封，F-5016~5021）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（携带稳定 code），接口层在此集中翻译为 {@code ErrorResponse}
 * （{@code {success:false, message}}）+ 合适 HTTP 状态码（backend-engineer §3.2）。仅作用于 compliance BC
 * 的 controller（{@code assignableTypes}），不影响其他 BC 的 advice。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link UserNotFoundException} → 404（注销目标不存在 / 已注销，账号域异常但本 BC 端点会抛）</li>
 *   <li>{@link ConsentRequiredException} → 403（F-5021 未同意条款拒绝调用）</li>
 *   <li>{@link CrossBorderRoutingDeniedException} → 403（F-5018 合规分组命中境外被拒）</li>
 *   <li>{@link InvalidRetentionPolicyException} → 400（F-5017 留存保留期非法）</li>
 * </ul></p>
 */
@RestControllerAdvice(assignableTypes = {AccountDeactivationController.class})
public class ComplianceExceptionHandler {

    /**
     * 注销目标用户不存在 / 已注销 → 404（F-5020）。
     *
     * <p>软删除用户由 {@code @SQLRestriction} 过滤，已注销账号再次注销将查不到 → 幂等地报 404，
     * 不泄露账号是否曾存在。</p>
     *
     * @param e 用户不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 未同意含出境/留存条款的协议 → 403（F-5021 同意闸门）。
     *
     * @param e 同意闸门未通过异常
     * @return 403 错误信封
     */
    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleConsentRequired(ConsentRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 合规分组命中境外渠道被拒 → 403（F-5018）。
     *
     * @param e 跨境路由拒绝异常
     * @return 403 错误信封
     */
    @ExceptionHandler(CrossBorderRoutingDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCrossBorder(CrossBorderRoutingDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * prompt 留存保留期配置非法 → 400（F-5017）。
     *
     * @param e 留存策略非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidRetentionPolicyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRetention(InvalidRetentionPolicyException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }
}
