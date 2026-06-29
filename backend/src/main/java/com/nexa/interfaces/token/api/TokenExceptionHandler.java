package com.nexa.interfaces.token.api;

import com.nexa.common.kernel.DomainException;

import com.nexa.domain.token.exception.InvalidTokenKeyException;
import com.nexa.domain.token.exception.InvalidTokenParameterException;
import com.nexa.domain.token.exception.TokenAccessDeniedException;
import com.nexa.domain.token.exception.TokenNotFoundException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 令牌管理接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码，
 * 用例/控制器因此不写 try/catch 模板代码（backend-engineer §3.2）。仅对 {@link TokenController} 与
 * {@link TokenUsageController} 生效（{@code assignableTypes}），不影响其他 bounded context。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidTokenParameterException} → 400（缺失/非法入参、批量超限）</li>
 *   <li>{@link TokenNotFoundException} → 404（按 id 操作但令牌缺失/已软删）</li>
 *   <li>{@link TokenAccessDeniedException} → 403（越权操作他人令牌，self-scope 违反）</li>
 *   <li>{@link InvalidTokenKeyException} → 401（tokenReadAuth：key 缺失/无效，F-3012）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 *
 * <p>安全：所有 message 不回显令牌明文 key（凭证从不进异常 message），也不暴露越权目标归属信息。</p>
 */
@RestControllerAdvice(assignableTypes = {TokenController.class, TokenUsageController.class})
public class TokenExceptionHandler {

    /**
     * 令牌入参非法 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidTokenParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidTokenParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 令牌不存在 → 404。
     *
     * @param e 令牌缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(TokenNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(TokenNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 越权操作他人令牌 → 403（self-scope 违反）。
     *
     * @param e 越权访问异常
     * @return 403 错误信封
     */
    @ExceptionHandler(TokenAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(TokenAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 令牌 key 无效/缺失 → 401（tokenReadAuth，F-3012）。
     *
     * @param e key 无效异常
     * @return 401 错误信封
     */
    @ExceptionHandler(InvalidTokenKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidKey(InvalidTokenKeyException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }
}
