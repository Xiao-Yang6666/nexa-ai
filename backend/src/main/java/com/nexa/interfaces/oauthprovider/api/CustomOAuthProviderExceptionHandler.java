package com.nexa.interfaces.oauthprovider.api;

import com.nexa.common.web.ApiResponse;
import com.nexa.domain.oauthprovider.exception.CustomOAuthProviderNotFoundException;
import com.nexa.domain.oauthprovider.exception.InvalidCustomOAuthProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * 自定义 OAuth provider 接口层全局异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>仅作用于 {@link CustomOAuthProviderController}（assignableTypes 限定，避免与账号域
 * {@code GlobalExceptionHandler} 冲突）。领域抛业务语义异常，本类集中翻译为 openapi
 * {@code ErrorResponse}（复用账号域 {@link ApiResponse} 信封保持全站一致）+ 合适状态码
 * （backend-engineer §3.2）。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidCustomOAuthProviderException} → 400（配置字段非法 / discovery 拉取失败）</li>
 *   <li>{@link CustomOAuthProviderNotFoundException} → 404（按 id 定位 provider 失败）</li>
 *   <li>Bean Validation / 约束校验失败 → 400</li>
 * </ul></p>
 */
@RestControllerAdvice(assignableTypes = {CustomOAuthProviderController.class})
public class CustomOAuthProviderExceptionHandler {

    /**
     * provider 配置非法 / discovery 拉取失败 → 400。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidCustomOAuthProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidCustomOAuthProviderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * provider 不存在 → 404。
     *
     * @param e 领域异常
     * @return 404 错误信封
     */
    @ExceptionHandler(CustomOAuthProviderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(CustomOAuthProviderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
}
