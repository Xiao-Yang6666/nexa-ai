package com.nexa.prefill.interfaces.api;

import com.nexa.shared.kernel.DomainException;
import com.nexa.prefill.domain.exception.InvalidPrefillParameterException;
import com.nexa.prefill.domain.exception.PrefillGroupNameConflictException;
import com.nexa.prefill.domain.exception.PrefillGroupNotFoundException;
import com.nexa.prefill.domain.exception.PrefillPersistenceException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 预填分组接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link DomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适 HTTP 状态码，
 * 用例/控制器因此不写 try/catch 模板（backend-engineer §3.2）。仅对预填域控制器包生效
 * （{@code basePackages}），不影响其它模块各自的异常处理器。</p>
 *
 * <p>状态码映射（对齐 openapi {@code /api/prefill_group*} 声明的 400/404/409）：
 * <ul>
 *   <li>{@link InvalidPrefillParameterException} → 400（name/type required、type 非法枚举）</li>
 *   <li>{@link PrefillGroupNotFoundException} → 404（id 不存在/已软删）</li>
 *   <li>{@link PrefillGroupNameConflictException} → 409（同 type 下 name 冲突）</li>
 *   <li>{@link PrefillPersistenceException} → 500（JSON 序列化失败，内部错误不泄露细节）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * message 透传领域异常的可读描述（不含敏感值），错误码 {@code code()} 不下发 body（前端按
 * message 提示即可，不暴露内部码语义）。</p>
 */
@RestControllerAdvice(basePackages = "com.nexa.prefill.interfaces.api")
public class PrefillExceptionHandler {

    /**
     * 参数非法 → 400（name/type required、type 非法枚举、id 缺失）。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidPrefillParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidPrefillParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 分组不存在 → 404（按 id 更新/软删除未命中）。
     *
     * @param e 不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(PrefillGroupNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(PrefillGroupNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 名称冲突 → 409（同 type 下 name 冲突）。
     *
     * @param e 冲突异常
     * @return 409 错误信封
     */
    @ExceptionHandler(PrefillGroupNameConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(PrefillGroupNameConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 持久化（JSON 序列化）失败 → 500（内部错误，不泄露底层细节给客户端）。
     *
     * @param e 持久化异常
     * @return 500 错误信封（稳定泛化提示）
     */
    @ExceptionHandler(PrefillPersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(PrefillPersistenceException e) {
        // 不回显底层 Jackson 错误细节（安全默认），仅给稳定泛化提示；错误链已在异常对象内供日志排查。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal error while processing prefill group"));
    }

    /**
     * 兜底：其余预填域领域异常 → 400（业务校验失败，客户端可纠正）。
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
