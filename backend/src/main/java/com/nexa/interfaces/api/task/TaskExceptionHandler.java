package com.nexa.interfaces.api.task;

import com.nexa.domain.kernel.HttpAwareDomainException;
import com.nexa.domain.task.exception.InvalidTaskParameterException;
import com.nexa.domain.task.exception.TaskNotFoundException;
import com.nexa.domain.task.exception.TaskPersistenceException;
import com.nexa.interfaces.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 异步任务中心接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link HttpAwareDomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适 HTTP 状态码
 * （backend-engineer §3.2）。仅对 task 域控制器包生效（{@code basePackages}），不影响其它模块。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidTaskParameterException} → 400（必填缺失、状态机非法转换）</li>
 *   <li>{@link TaskNotFoundException} → 404（task_id 不存在 / 非本人）</li>
 *   <li>{@link TaskPersistenceException} → 500（JSON 序列化失败，内部错误不泄露细节）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice(basePackages = "com.nexa.interfaces.api.task")
public class TaskExceptionHandler {

    /**
     * 参数非法 / 状态机非法转换 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidTaskParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidTaskParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 任务不存在 / 非本人 → 404（不区分两者，避免泄露任务存在性，PRD AT-2）。
     *
     * @param e 不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(TaskNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 持久化（JSON 序列化）失败 → 500（不泄露底层细节给客户端）。
     *
     * @param e 持久化异常
     * @return 500 错误信封（稳定泛化提示）
     */
    @ExceptionHandler(TaskPersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(TaskPersistenceException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal error while processing task"));
    }

    /**
     * 兜底：其余 task 域领域异常 → 按其建议 HTTP 状态码。
     *
     * @param e 领域异常
     * @return 错误信封（用领域异常自带的 httpStatus）
     */
    @ExceptionHandler(HttpAwareDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(HttpAwareDomainException e) {
        return ResponseEntity.status(HttpStatus.valueOf(e.httpStatus()))
                .body(ApiResponse.error(e.getMessage()));
    }
}
