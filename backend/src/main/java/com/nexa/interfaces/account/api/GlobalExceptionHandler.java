package com.nexa.interfaces.account.api;

import com.nexa.common.kernel.DomainException;

import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.exception.InvalidOAuthStateException;
import com.nexa.domain.account.exception.InvalidResetTokenException;
import com.nexa.domain.account.exception.OAuthBindingConflictException;
import com.nexa.domain.account.exception.OAuthBindingNotFoundException;
import com.nexa.domain.account.exception.RegisterDisabledException;
import com.nexa.domain.account.exception.RoleHierarchyViolationException;
import com.nexa.domain.account.exception.UserAlreadyExistsException;
import com.nexa.domain.account.exception.UserDisabledException;
import com.nexa.domain.account.exception.UserNotFoundException;
import com.nexa.domain.account.exception.VerificationCodeException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账号接口层异常处理（协议翻译：账号域<b>领域异常</b> → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此
 * 集中翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码。
 * 用例/控制器因此不写 try/catch 模板代码（backend-engineer §3.2 错误用明确类型而非散落处理）。</p>
 *
 * <p>仅保留账号域<b>领域异常</b>映射；协议级异常（Bean Validation / 约束校验 / 非法 JSON / 兜底 500）
 * 已上提到全局 {@code com.nexa.common.web.GlobalApiExceptionHandler} 统一处理，不再在各模块重复。</p>
 *
 * <p>状态码映射（对齐 openapi：账号注册/登录失败均归 400 BadRequestError，封禁归 403）：
 * <ul>
 *   <li>{@link UserAlreadyExistsException} → 400（用户名已存在）</li>
 *   <li>{@link InvalidCredentialException} → 400（参数非法 / 账号或密码错误，统一不区分防枚举）</li>
 *   <li>{@link RegisterDisabledException} → 403（注册功能被系统开关关闭）</li>
 *   <li>{@link UserDisabledException} → 403（账号被封禁）</li>
 *   <li>{@link VerificationCodeException} → 400（验证码错误/过期，F-1005）</li>
 *   <li>{@link InvalidResetTokenException} → 400（重置令牌无效/过期，F-1007）</li>
 *   <li>{@link RoleHierarchyViolationException} → 403（管理端角色越权，F-1009~1012，AC-10）</li>
 *   <li>{@link UserNotFoundException} → 404（管理端按 id 定位目标用户失败，F-1010/1011）</li>
 * </ul>
 * 错误 message 透传领域 message（已设计为不泄露可枚举信息）。</p>
 */
@RestControllerAdvice(assignableTypes = {
        UserController.class, AuthEmailController.class, AdminUserController.class,
        OAuthController.class, WeChatController.class, OAuthBindingController.class})
public class GlobalExceptionHandler {

    /**
     * 用户名已存在 → 400。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserExists(UserAlreadyExistsException e) {
        // 不原样回显冲突用户名（避免账号枚举），用稳定面向用户的提示。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("username already exists"));
    }

    /**
     * 凭证/输入非法、账号或密码错误 → 400。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidCredentialException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredential(InvalidCredentialException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 注册被系统开关关闭 → 403。
     *
     * @param e 领域异常
     * @return 403 错误信封
     */
    @ExceptionHandler(RegisterDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleRegisterDisabled(RegisterDisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 账号被封禁 → 403。
     *
     * @param e 领域异常
     * @return 403 错误信封
     */
    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserDisabled(UserDisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }
    /**
     * 邮箱验证码错误/过期 → 400（F-1005）。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(VerificationCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationCode(VerificationCodeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 密码重置令牌无效/过期 → 400（F-1007，对齐 openapi reset 400 BadRequestError）。
     *
     * @param e 领域异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidResetToken(InvalidResetTokenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }
    /**
     * 管理端角色越权护栏违反 → 403（F-1009~1012，对齐 openapi {@code ForbiddenError}）。
     *
     * <p>领域规则来源：PRD AC-10「操作者不可操作/提升到 ≥ 自身角色的用户」。这是纯领域护栏，
     * 在聚合根 {@code User} 内守护并抛本异常；接口层在此映射 403 越权拒绝态。message 透传领域描述
     * （已设计为不回显可枚举的目标账号细节）。</p>
     *
     * @param e 越权异常
     * @return 403 错误信封
     */
    @ExceptionHandler(RoleHierarchyViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleRoleHierarchy(RoleHierarchyViolationException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 管理端目标用户不存在 → 404（F-1010/1011）。
     *
     * <p>管理端按 {@code id} 定位目标用户失败时抛出（AC-10 §2 前置条件「目标用户存在」未满足）。
     * message 含 id 上下文，便于排障；不泄露其它账号信息。</p>
     *
     * @param e 用户不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * OAuth state 无效/过期/已消费 → 400（CSRF 校验失败，F-1015/F-1016）。
     *
     * <p>回调带回的 state 无法从 StateStore 一次性取回（不存在/过期/已消费）时抛出，视为可能的
     * CSRF / 重放，拒绝回调。不回显 state 细节（避免给攻击者反馈），用稳定面向用户的提示。</p>
     *
     * @param e state 校验异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidOAuthStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOAuthState(InvalidOAuthStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * OAuth 绑定冲突 → 409（F-1016~1020）。
     *
     * <p>某第三方账号（provider + providerUserId）已绑定到<b>另一个</b>本站用户，又被请求绑定到
     * 当前用户时抛出（违反每 provider 一账号唯一，DB-SCHEMA §13 复合唯一索引）。映射 409 Conflict
     * 表达资源状态冲突；message 透传领域描述（不回显对端 userId 等敏感细节）。</p>
     *
     * @param e 绑定冲突异常
     * @return 409 错误信封
     */
    @ExceptionHandler(OAuthBindingConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthBindingConflict(OAuthBindingConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * OAuth 绑定不存在 → 404（解绑 F-1026/F-1027）。
     *
     * <p>本人/管理端按 {@code (userId, providerId)} 定位待解绑的自定义 provider 绑定未命中时抛出
     * （该用户在该 provider 下无绑定，或 provider_id 不存在）。映射 404 NotFound。message 含定位上下文，
     * 不泄露其它账号绑定信息。</p>
     *
     * @param e 绑定不存在异常
     * @return 404 错误信封
     */
    @ExceptionHandler(OAuthBindingNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthBindingNotFound(OAuthBindingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
}
