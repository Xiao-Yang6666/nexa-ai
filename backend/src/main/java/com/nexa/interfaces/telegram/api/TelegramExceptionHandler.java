package com.nexa.interfaces.telegram.api;

import com.nexa.domain.telegram.exception.InvalidTelegramAuthException;
import com.nexa.domain.telegram.exception.TelegramBindingConflictException;
import com.nexa.domain.telegram.exception.TelegramUserNotFoundException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Telegram 接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code telegram.domain.exception.DomainException} 子类），接口层在此
 * 集中翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码
 * （backend-engineer §3.2）。用 {@code assignableTypes} 限定仅作用于 {@link TelegramController}，
 * 不影响其它 BC（与 account/log/task 的 per-BC advice 风格一致）。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidTelegramAuthException} → 400（HMAC 校验失败 / 授权过期 / 参数非法 / 未启用，F-1051/1053）</li>
 *   <li>{@link TelegramBindingConflictException} → 409（Telegram 账号已被他人绑定，F-1054）</li>
 *   <li>{@link TelegramUserNotFoundException} → 404（绑定/会话用户不存在）</li>
 * </ul>
 * 错误 message 透传领域 message（已设计为不泄露 hash/token/占用方账号等敏感信息）。</p>
 */
@RestControllerAdvice(assignableTypes = {TelegramController.class})
public class TelegramExceptionHandler {

    /**
     * Telegram 授权非法（HMAC 失败 / 过期 / 参数非法 / 未启用）→ 400（F-1051/F-1053）。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidTelegramAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAuth(InvalidTelegramAuthException e) {
        // 不回显 hash/token 细节（不给攻击者反馈具体失败原因），透传稳定语义 message。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * Telegram 绑定冲突 → 409（F-1054）。
     *
     * <p>目标 Telegram 账号已绑到另一本站账号时抛出。映射 409 Conflict 表达资源状态冲突；
     * message 不回显占用方 userId（防账号枚举）。</p>
     *
     * @param e 绑定冲突异常
     * @return 409 错误信封
     */
    @ExceptionHandler(TelegramBindingConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(TelegramBindingConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 绑定/会话用户不存在 → 404。
     *
     * @param e 用户不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(TelegramUserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(TelegramUserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
}
