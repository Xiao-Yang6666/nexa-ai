package com.nexa.interfaces.api.modelgroup;

import com.nexa.common.kernel.DomainException;
import com.nexa.domain.modelgroup.exception.InvalidModelGroupParameterException;
import com.nexa.domain.modelgroup.exception.ModelGroupCodeConflictException;
import com.nexa.domain.modelgroup.exception.ModelGroupCodeNotFoundException;
import com.nexa.domain.modelgroup.exception.ModelGroupNotFoundException;
import com.nexa.domain.modelgroup.exception.ModelGroupPersistenceException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 模型组接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link DomainException} 子类，携带稳定 code），接口层在此集中翻译为
 * {@code {success:false, message}} + 合适 HTTP 状态码，用例/控制器因此不写 try/catch 模板
 * （backend-engineer §3.2）。仅对模型组域控制器包生效（{@code basePackages}）。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidModelGroupParameterException} → 400</li>
 *   <li>{@link ModelGroupNotFoundException} → 404</li>
 *   <li>{@link ModelGroupCodeConflictException} → 409</li>
 *   <li>{@link ModelGroupPersistenceException} → 500（内部错误不泄露细节）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul></p>
 */
@RestControllerAdvice(basePackages = "com.nexa.interfaces.api.modelgroup")
public class ModelGroupExceptionHandler {

    /**
     * 参数非法 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidModelGroupParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidModelGroupParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 模型组/授权记录不存在 → 404。
     *
     * @param e 不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(ModelGroupNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ModelGroupNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 模型组（按 code）不存在 → 404。
     *
     * @param e 按 code 不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(ModelGroupCodeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCodeNotFound(ModelGroupCodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 编码冲突 → 409。
     *
     * @param e 冲突异常
     * @return 409 错误信封
     */
    @ExceptionHandler(ModelGroupCodeConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ModelGroupCodeConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 持久化失败 → 500（内部错误，不泄露底层细节）。
     *
     * @param e 持久化异常
     * @return 500 错误信封
     */
    @ExceptionHandler(ModelGroupPersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(ModelGroupPersistenceException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal error while processing model group"));
    }

    /**
     * 兜底：其余模型组域领域异常 → 400。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }
}
