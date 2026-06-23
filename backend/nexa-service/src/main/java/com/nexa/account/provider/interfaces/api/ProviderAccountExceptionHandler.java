package com.nexa.account.provider.interfaces.api;

import com.nexa.account.provider.domain.exception.AccountNotFoundException;
import com.nexa.account.provider.domain.exception.InvalidAccountParameterException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 供应商账号管理接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>仅对 {@link AccountController} 生效（{@code assignableTypes}），不影响其他 bounded context。
 * 状态码映射：
 * <ul>
 *   <li>{@link InvalidAccountParameterException} → 400（缺失/非法入参）</li>
 *   <li>{@link AccountNotFoundException} → 404（按 id 操作但账号缺失）</li>
 * </ul>
 * </p>
 *
 * <p>安全：所有 message 不回显 credentials 等敏感凭证。</p>
 */
@RestControllerAdvice(assignableTypes = {AccountController.class})
public class ProviderAccountExceptionHandler {

    /**
     * 账号入参非法 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidAccountParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidAccountParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 账号不存在 → 404。
     *
     * @param e 账号缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }
}
