package com.nexa.interfaces.passkey.api;

import com.nexa.common.web.ApiResponse;
import com.nexa.domain.passkey.exception.InvalidPasskeyCeremonyException;
import com.nexa.domain.passkey.exception.PasskeyNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Passkey 接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>仅作用于 passkey 域控制器（assignableTypes 限定 {@link PasskeyController} +
 * {@link AdminPasskeyController}，避免与账号域 {@code GlobalExceptionHandler} 及 oauthprovider 域
 * 处理器冲突）。领域抛业务语义异常，本类集中翻译为 openapi {@code ErrorResponse}（复用账号域
 * {@link ApiResponse} 信封保持全站一致）+ 合适状态码（backend-engineer §3.2）。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidPasskeyCeremonyException} → 400（ceremony 校验失败 / challenge 失效 / 字段非法）</li>
 *   <li>{@link PasskeyNotFoundException} → 404（凭据/用户不存在，对齐 DB-SCHEMA §16 ErrPasskeyNotFound）</li>
 * </ul></p>
 */
@RestControllerAdvice(assignableTypes = {PasskeyController.class, AdminPasskeyController.class})
public class PasskeyExceptionHandler {

    /**
     * ceremony 校验失败 / 字段非法 → 400。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidPasskeyCeremonyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCeremony(InvalidPasskeyCeremonyException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * passkey 凭据 / 用户不存在 → 404。
     *
     * @param e 领域异常
     * @return 404 错误信封
     */
    @ExceptionHandler(PasskeyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(PasskeyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
}
