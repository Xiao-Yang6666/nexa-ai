package com.nexa.playground.interfaces.api;

import com.nexa.playground.domain.exception.DomainException;
import com.nexa.playground.domain.exception.InvalidPlaygroundRequestException;
import com.nexa.playground.domain.exception.PlaygroundAccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Playground 接口层异常处理（领域异常 → HTTP 状态码 + OpenAI 兼容错误信封，F-4038）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link DomainException} 子类），接口层在此集中翻译为 HTTP 状态码
 * + 错误信封 {@code {error:{type,message,code}}}（OpenAI 兼容，对齐 openapi {@code ErrorResponse} 与
 * {@code /pg/chat/completions} 的 403 定义），控制器/用例不写 try/catch（backend-engineer §3.2）。</p>
 *
 * <p>关键映射：{@link PlaygroundAccessDeniedException} → 403「暂不支持使用 access token」
 * （F-4038 关键安全闸 ErrorCodeAccessDenied）。安全：message 在 domain 层即脱敏，不回显客户正文/凭据。</p>
 */
@RestControllerAdvice(assignableTypes = PlaygroundController.class)
public class PlaygroundExceptionHandler {

    /**
     * 禁用 access token（F-4038 安全闸）→ 403 ErrorCodeAccessDenied。
     */
    @ExceptionHandler(PlaygroundAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(PlaygroundAccessDeniedException e) {
        return toError(403, "access_denied", e.getMessage(), e.code());
    }

    /**
     * 入参非法（model/messages 空、分组缺失）→ 400。
     */
    @ExceptionHandler(InvalidPlaygroundRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(InvalidPlaygroundRequestException e) {
        return toError(400, "invalid_request_error", e.getMessage(), e.code());
    }
    /**
     * 其余 Playground 领域异常 → 按其建议状态码翻译。
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(DomainException e) {
        return toError(e.httpStatus(), "server_error", e.getMessage(), e.code());
    }

    /**
     * 构造 OpenAI 兼容错误信封 {@code {error:{type,message,code}}}。
     *
     * @param status  HTTP 状态码
     * @param type    错误类型
     * @param message 错误描述（已脱敏）
     * @param code    稳定业务错误码（可空）
     * @return 错误响应实体
     */
    private ResponseEntity<Map<String, Object>> toError(int status, String type, String message, String code) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", type);
        error.put("message", message);
        if (code != null) {
            error.put("code", code);
        }
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
