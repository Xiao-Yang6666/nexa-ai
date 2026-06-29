package com.nexa.interfaces.sensitiveverify.api;

import com.nexa.shared.web.ApiResponse;
import com.nexa.domain.sensitiveverify.exception.InvalidVerificationRequestException;
import com.nexa.domain.sensitiveverify.exception.SensitiveActionVerificationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 敏感动作二次验证接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封，F-1038）。
 *
 * <p>仅作用于本上下文控制器（{@code assignableTypes} 限定 {@link SensitiveVerifyController}，
 * 避免与账号域 {@code GlobalExceptionHandler}、passkey 域、oauthprovider 域处理器冲突）。
 * 领域抛业务语义异常，本类集中翻译为 openapi {@code ErrorResponse}（复用账号域 {@link ApiResponse}
 * 信封保持全站一致）+ 合适状态码（backend-engineer §3.2）。</p>
 *
 * <p>状态码映射（对齐 openapi {@code POST /api/verify} responses）：
 * <ul>
 *   <li>{@link SensitiveActionVerificationFailedException} → 403（因子均未通过，{@code ForbiddenError}）</li>
 *   <li>{@link InvalidVerificationRequestException} → 400（未提供任何因子 / 凭据载荷结构非法）</li>
 * </ul>
 * 错误 message 为中性描述，不回显提交的凭据原文（零敏感泄露，§3.4）。</p>
 */
@RestControllerAdvice(assignableTypes = SensitiveVerifyController.class)
public class SensitiveVerifyExceptionHandler {

    /**
     * 二次验证未通过 → 403。
     *
     * @param e 领域异常
     * @return 403 错误信封
     */
    @ExceptionHandler(SensitiveActionVerificationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationFailed(
            SensitiveActionVerificationFailedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 请求未携带任何可验证凭据 / 凭据载荷结构非法 → 400。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidVerificationRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
            InvalidVerificationRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }
}
