package com.nexa.interfaces.api.account.provider;

import com.nexa.application.account.provider.port.ProviderModelProbePort.ProviderProbeException;
import com.nexa.domain.account.provider.exception.AccountNotFoundException;
import com.nexa.domain.account.provider.exception.InvalidAccountParameterException;
import com.nexa.interfaces.web.ApiResponse;
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
 *   <li>{@link ProviderProbeException} → 502（探测上游模型列表失败）</li>
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

    /**
     * 探测上游模型列表失败 → 502（上游不可达/鉴权失败/解析失败）。
     *
     * @param e 探测异常
     * @return 502 错误信封（不含敏感凭证）
     */
    @ExceptionHandler(ProviderProbeException.class)
    public ResponseEntity<ApiResponse<Void>> handleProbeFailure(ProviderProbeException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }
}
