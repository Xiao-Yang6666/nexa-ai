package com.nexa.account.interfaces.api;

import com.nexa.account.application.SendPasswordResetEmailUseCase;
import com.nexa.account.application.SendVerificationCodeUseCase;
import com.nexa.account.interfaces.api.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号认证邮件接口层控制器（发验证码 / 发重置密码邮件）。
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP query 参数 ⇄ 用例入参），不含业务逻辑
 * （backend-engineer §2.1）。这两个端点的 base path 不在 {@code /api/user} 下
 * （openapi 标 {@code /api/verification}、{@code /api/reset_password}），故独立成 controller。</p>
 *
 * <p>对齐 openapi.yaml：{@code GET /api/verification?email=}（F-1004 发注册/找回验证码）、
 * {@code GET /api/reset_password?email=}（F-1006 发重置密码邮件）。两者均 {@code security: []}（公开）。
 * 领域/业务异常由 {@code GlobalExceptionHandler} 统一翻译。</p>
 */
@RestController
@Validated
public class AuthEmailController {

    private final SendVerificationCodeUseCase sendVerificationCodeUseCase;
    private final SendPasswordResetEmailUseCase sendPasswordResetEmailUseCase;

    /**
     * @param sendVerificationCodeUseCase   发验证码用例（F-1004）
     * @param sendPasswordResetEmailUseCase 发重置密码邮件用例（F-1006）
     */
    public AuthEmailController(SendVerificationCodeUseCase sendVerificationCodeUseCase,
                              SendPasswordResetEmailUseCase sendPasswordResetEmailUseCase) {
        this.sendVerificationCodeUseCase = sendVerificationCodeUseCase;
        this.sendPasswordResetEmailUseCase = sendPasswordResetEmailUseCase;
    }

    /**
     * 发送注册/找回邮箱验证码（F-1004）。
     *
     * <p>对齐 openapi.yaml {@code GET /api/verification?email=}。仅回执成功，不下发验证码本身。</p>
     *
     * @param email 目标邮箱（query 参数，必填）
     * @return 成功信封
     */
    @GetMapping("/api/verification")
    public ApiResponse<Void> sendVerificationCode(
            @RequestParam("email") @NotBlank(message = "email must not be blank") String email) {
        sendVerificationCodeUseCase.send(email);
        return ApiResponse.ok("verification code sent");
    }

    /**
     * 发送重置密码邮件（F-1006）。
     *
     * <p>对齐 openapi.yaml {@code GET /api/reset_password?email=}。<b>非注册邮箱仍返回成功</b>
     * （防枚举，用例内静默处理），接口层无差别回执成功。</p>
     *
     * @param email 目标邮箱（query 参数，必填）
     * @return 成功信封
     */
    @GetMapping("/api/reset_password")
    public ApiResponse<Void> sendPasswordResetEmail(
            @RequestParam("email") @NotBlank(message = "email must not be blank") String email) {
        sendPasswordResetEmailUseCase.send(email);
        return ApiResponse.ok("password reset email sent");
    }
}
