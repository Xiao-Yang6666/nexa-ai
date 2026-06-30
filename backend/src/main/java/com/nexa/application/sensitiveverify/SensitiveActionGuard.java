package com.nexa.application.sensitiveverify;

import org.springframework.stereotype.Component;
import com.nexa.application.sensitiveverify.command.VerifySensitiveActionCommand;

/**
 * 可复用的二次验证检查点（F-1038，供改密/解绑等敏感操作前置守卫）。
 *
 * <p>F-1038 不仅是一个独立端点，更要提供「可复用的二次验证检查点，供改密/解绑等敏感操作前调用」。
 * 本组件即该检查点：任何敏感操作的应用服务可注入它，在执行受保护动作<b>之前</b>调用
 * {@link #guard(VerifySensitiveActionCommand)}，验证未通过会抛领域异常中断动作（接口层翻 403），
 * 通过则正常返回继续执行。这样验证逻辑集中一处、各处复用，避免散落重复（DRY + 单一规则源）。</p>
 *
 * <p>设计：薄包装 {@link VerifySensitiveActionUseCase}（裁决规则仍在领域服务）。独立成 {@code @Component}
 * 而非让各处直接依赖用例，是为给\"前置守卫\"语义一个清晰的复用入口，并便于未来叠加策略
 * （如按动作类型要求特定因子、记录二次验证审计、限流等）而不动调用方。</p>
 *
 * <p>用法示例（改密用例内）：
 * <pre>{@code
 *   // 改密前置二次验证（F-1038 复用检查点）
 *   sensitiveActionGuard.guard(new VerifySensitiveActionCommand(userId, pwd, totp, passkeyJson));
 *   // 验证通过才继续改密
 *   user.changePassword(...);
 * }</pre></p>
 */
@Component
public class SensitiveActionGuard {

    private final VerifySensitiveActionUseCase verifyUseCase;

    /**
     * @param verifyUseCase 二次验证用例（承载裁决编排）
     */
    public SensitiveActionGuard(VerifySensitiveActionUseCase verifyUseCase) {
        this.verifyUseCase = verifyUseCase;
    }

    /**
     * 二次验证守卫：验证通过放行（正常返回），否则抛领域异常中断敏感动作。
     *
     * <p>敏感操作应在执行受保护动作前先调用本方法；抛出的异常由调用方所在控制器的异常处理器
     * （或本上下文 {@code SensitiveVerifyExceptionHandler}）翻译为 403/400。</p>
     *
     * @param command 验证命令（会话用户 + 各因子凭据）
     * @throws com.nexa.domain.sensitiveverify.exception.InvalidVerificationRequestException        未提供任何因子（400）
     * @throws com.nexa.domain.sensitiveverify.exception.SensitiveActionVerificationFailedException 因子均未通过（403）
     */
    public void guard(VerifySensitiveActionCommand command) {
        verifyUseCase.verify(command);
    }
}
