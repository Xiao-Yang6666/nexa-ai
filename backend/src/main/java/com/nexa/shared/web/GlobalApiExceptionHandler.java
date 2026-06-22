package com.nexa.shared.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全站协议级异常兜底处理（最低优先级 advice）。
 *
 * <p>职责边界（与各 bounded context 自己的 {@code XxxExceptionHandler} 分工）：
 * <ul>
 *   <li><b>本类（全局）</b>：处理与业务语义无关的<b>协议级</b>异常——非法 JSON、Bean Validation
 *       校验失败、{@code @RequestParam} 约束/缺失/类型不匹配，以及任何未被捕获的兜底异常。这些处理
 *       在原架构中被各模块重复实现（{@code HttpMessageNotReadableException} 曾复制 13 份），且各模块
 *       <b>全部缺失</b> catch-all 兜底（未预期 RuntimeException 会走 Spring 默认错误页、破坏统一信封）。
 *       收敛到此处既去重又补洞。</li>
 *   <li><b>各模块 advice（局部）</b>：仅保留领域异常 → HTTP 状态码的<b>业务映射</b>（如
 *       {@code ChannelUpstreamException} → 502），这部分依赖具体 context 的业务语义，不应上提。</li>
 * </ul></p>
 *
 * <p>优先级：标注 {@link Order}({@link Ordered#LOWEST_PRECEDENCE})，确保模块局部 advice（更贴近具体
 * controller、处理更具体的领域异常类型）优先匹配；本全局 advice 只在没有更具体处理者时兜底。协议级异常
 * （JSON/校验等）只有本 advice 处理，不与模块 advice 竞争。</p>
 *
 * <p>安全：catch-all 不回显异常堆栈/message（避免泄露内部细节），仅记服务端日志 + 返回稳定提示。</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    /**
     * 请求体不可读（非法 JSON / 反序列化失败）→ 400。
     *
     * @param e 反序列化异常
     * @return 400 错误信封
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("invalid request body"));
    }

    /**
     * 请求体 Bean Validation 校验失败 → 400（聚合字段级错误为可读 message，不泄露内部细节）。
     *
     * @param e 校验异常
     * @return 400 错误信封
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "request validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    /**
     * 方法级参数约束校验失败（{@code @RequestParam} 上的约束注解，由 {@code @Validated} 触发）→ 400。
     *
     * @param e 约束校验异常
     * @return 400 错误信封
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "request validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    /**
     * 必填 query/form 参数缺失 → 400。
     *
     * @param e 参数缺失异常
     * @return 400 错误信封
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("missing required parameter: " + e.getParameterName()));
    }

    /**
     * 参数类型不匹配（如期望 long 传了非数字）→ 400。
     *
     * @param e 类型不匹配异常
     * @return 400 错误信封
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("invalid parameter: " + e.getName()));
    }

    /**
     * 兜底：任何未被更具体处理者（模块 advice 或上述协议处理）捕获的异常 → 500。
     *
     * <p>补齐原架构缺失的 catch-all：未预期 {@code RuntimeException}（NPE、越界等）不再走 Spring
     * 默认错误页，而是统一信封 + 服务端日志。不回显异常细节（安全）。</p>
     *
     * @param e 未预期异常
     * @return 500 错误信封
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unhandled exception reached global handler", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal server error"));
    }
}
