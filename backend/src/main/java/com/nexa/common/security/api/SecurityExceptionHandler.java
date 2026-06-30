package com.nexa.common.security.api;

import com.nexa.common.web.ApiResponse;
import com.nexa.common.security.exception.AccessDeniedException;
import com.nexa.common.security.exception.AuthenticationRequiredException;
import com.nexa.common.security.exception.FieldEncryptionException;
import com.nexa.common.security.exception.InsecureTransportException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全站安全横切异常兜底处理（接口层，统一输入校验 + 安全异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>定位：<b>应用级兜底</b>处理器（未用 {@code assignableTypes} 限定，覆盖所有 controller），
 * 用最低优先级（{@link Ordered#LOWEST_PRECEDENCE}）兜住——各 bounded context 自带的、用
 * {@code assignableTypes} 精确限定的 {@code @RestControllerAdvice}（如账号域 GlobalExceptionHandler）
 * 优先级更高、就近匹配，本兜底仅在它们未覆盖时生效，二者不冲突。</p>
 *
 * <p>职责（对应本切片四功能）：
 * <ul>
 *   <li><b>统一输入校验</b>：Bean Validation 失败（body 的 {@link MethodArgumentNotValidException}、
 *       参数的 {@link ConstraintViolationException}，含 {@code @SafeText} 等通用约束）统一翻 400，
 *       聚合字段错误为可读 message，不暴露内部细节；</li>
 *   <li><b>HTTPS 强制</b>：{@link InsecureTransportException} → 403；</li>
 *   <li><b>敏感数据加密</b>：{@link FieldEncryptionException} 属服务端内部错误（密钥/完整性）→ 500，
 *       且<b>绝不</b>把底层 message/密钥/密文回显给客户，只给稳定通用提示，细节进服务端日志。</li>
 * </ul>
 * 错误信封复用全站统一的 {@link ApiResponse}（对齐 openapi {@code ErrorResponse}）。</p>
 *
 * <p>设计依据：backend-engineer §3.2 错误用明确类型集中翻译、§3.4 不信任外部输入。</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 请求体 Bean Validation 失败 → 400（统一输入校验）。
     *
     * @param e 校验异常
     * @return 400 错误信封
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "request validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    /**
     * 方法参数（{@code @RequestParam}/{@code @PathVariable}）约束校验失败 → 400。
     *
     * @param e 约束校验异常
     * @return 400 错误信封
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "request validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    /**
     * 非安全传输（明文 HTTP 命中需 HTTPS 端点的 reject 策略）→ 403。
     *
     * @param e 非安全传输异常
     * @return 403 错误信封
     */
    @ExceptionHandler(InsecureTransportException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsecureTransport(InsecureTransportException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 敏感字段加解密失败 → 500（服务端内部错误，不外泄细节）。
     *
     * <p>加解密失败属密钥配置错误或密文完整性问题（被篡改/密钥轮换不一致），是服务端侧问题而非
     * 客户输入错误，故归 500。安全要求：响应只给稳定通用提示，绝不回显底层 message（可能含密码学
     * 上下文）、更不含明文/密文/密钥——真正的细节进服务端结构化日志由运维排查。</p>
     *
     * @param e 加解密异常
     * @return 500 错误信封（通用提示）
     */
    @ExceptionHandler(FieldEncryptionException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldEncryption(FieldEncryptionException e) {
        // 不回显 e.getMessage()，避免侧信道泄露密码学上下文。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal security error"));
    }

    /**
     * 未认证（缺少/无效身份凭据）→ 401（三级鉴权 F-5031，对齐 openapi {@code UnauthorizedError}）。
     *
     * <p>受保护端点未解析出有效操作者（缺凭据/令牌过期/签名非法/声明缺失）时，由方法级权限拦截器
     * {@code RequireRoleInterceptor} 或 {@code @CurrentActor} 解析器抛出。message 用稳定通用提示，
     * 不回显令牌细节（避免给攻击者枚举反馈）。</p>
     *
     * @param e 未认证异常
     * @return 401 错误信封
     */
    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationRequired(AuthenticationRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 越权拒绝（已认证但权限级别/范围不足）→ 403（F-5031 越权路由 403，对齐 openapi {@code ForbiddenError}）。
     *
     * <p>AdminAuth/RootAuth 级别不满足、或 self-scope 跨用户访问、或管理端角色层级护栏违反时，
     * 由 {@code AuthenticatedActor} 的护栏方法 / 方法级权限拦截器抛出。message 不回显目标资源归属
     * （避免账号枚举）。</p>
     *
     * @param e 越权异常
     * @return 403 错误信封
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }
}
