package com.nexa.interfaces.api.relay;

import com.nexa.common.kernel.HttpAwareDomainException;
import com.nexa.domain.relay.exception.InvalidRelayParameterException;
import com.nexa.domain.relay.exception.ModelMappingException;
import com.nexa.domain.relay.exception.ProtocolConversionException;
import com.nexa.domain.relay.exception.RelayNotImplementedException;
import com.nexa.domain.relay.exception.UpstreamException;
import com.nexa.domain.relay.exception.VideoTaskException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Relay 网关接口层异常处理（协议翻译：领域/集成异常 → HTTP 状态码 + 错误信封，RL-3 re_fmt）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link HttpAwareDomainException} 子类），接口层在此集中翻译为 HTTP 状态码
 * + 错误信封（OpenAI 格式 {@code {error:{type,message,code}}}），用例/控制器不写 try/catch。</p>
 *
 * <p>安全：message 不回显上游凭证/token key（均在 domain 层脱敏，backend-engineer §3.2）。</p>
 */
@RestControllerAdvice(assignableTypes = {RelayController.class, RelayMappingController.class})
public class RelayExceptionHandler {

    @ExceptionHandler(InvalidRelayParameterException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidParam(InvalidRelayParameterException e) {
        return toError(400, "invalid_request_error", e.getMessage(), e.code());
    }

    @ExceptionHandler(ModelMappingException.class)
    public ResponseEntity<Map<String, Object>> handleMapping(ModelMappingException e) {
        return toError(e.httpStatus(), "model_error", e.getMessage(), e.code());
    }

    @ExceptionHandler(VideoTaskException.class)
    public ResponseEntity<Map<String, Object>> handleVideo(VideoTaskException e) {
        return toError(e.httpStatus(), "video_error", e.getMessage(), e.code());
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<Map<String, Object>> handleUpstream(UpstreamException e) {
        return toError(502, "upstream_error", e.getMessage(), e.code());
    }

    @ExceptionHandler(RelayNotImplementedException.class)
    public ResponseEntity<Map<String, Object>> handleNotImplemented(RelayNotImplementedException e) {
        return toError(501, "not_implemented", e.getMessage(), e.code());
    }

    @ExceptionHandler(ProtocolConversionException.class)
    public ResponseEntity<Map<String, Object>> handleConversion(ProtocolConversionException e) {
        return toError(500, "server_error", "protocol conversion failed", e.code());
    }

    @ExceptionHandler(HttpAwareDomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(HttpAwareDomainException e) {
        return toError(e.httpStatus(), "server_error", e.getMessage(), e.code());
    }

    private ResponseEntity<Map<String, Object>> toError(int status, String type, String message, String code) {
        Map<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("type", type);
        error.put("message", message);
        if (code != null) error.put("code", code);
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
