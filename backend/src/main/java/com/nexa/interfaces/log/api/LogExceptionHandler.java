package com.nexa.interfaces.log.api;

import com.nexa.domain.log.exception.InvalidLogQueryException;
import com.nexa.domain.log.exception.LogPersistenceException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 日志与用量接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此集中翻译
 * 为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适状态码，用例/控制器不写
 * try/catch 模板（backend-engineer §3.2）。仅对 log BC 的控制器生效（{@code assignableTypes}），
 * 不影响其他 bounded context。鉴权异常（401/403）由全站兜底 {@code SecurityExceptionHandler} 处理。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidLogQueryException} → 400（period 非法 / target_timestamp 缺失 / 跨度超 1 月 / 无效令牌）；</li>
 *   <li>{@link LogPersistenceException} → 500（数据访问失败，不回显底层 message）。</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice(assignableTypes = {
        LogController.class,
        SelfLogController.class,
        LogTokenController.class,
        DataController.class,
        SelfDataController.class,
        RankingController.class
})
public class LogExceptionHandler {

    /**
     * 日志查询入参非法 → 400（含 period 非法、target_timestamp 缺失、跨度超 1 月、无效令牌）。
     *
     * @param e 入参非法异常
     * @return 400 错误信封（透传领域文案，对齐现网）
     */
    @ExceptionHandler(InvalidLogQueryException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidQuery(InvalidLogQueryException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 日志读侧持久化失败 → 500（不回显底层细节，避免侧信道泄露）。
     *
     * @param e 持久化异常
     * @return 500 错误信封（稳定通用提示）
     */
    @ExceptionHandler(LogPersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(LogPersistenceException e) {
        // 不回显 e.getMessage()/根因（可能含 SQL 片段），细节进服务端日志由运维排查。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal log query error"));
    }
}
