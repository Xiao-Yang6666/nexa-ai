package com.nexa.interfaces.compliance.api;

import com.nexa.shared.web.ApiResponse;
import com.nexa.application.compliance.DeactivateAccountCommand;
import com.nexa.application.compliance.DeactivateAccountUseCase;
import com.nexa.application.compliance.port.AccountDeactivationCascade;
import com.nexa.interfaces.compliance.api.dto.AccountDeactivationView;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号注销接口层控制器（F-5020 账号注销级联删除，DC-003/DC-011）。
 *
 * <p>DDD 铁律：接口层只做协议翻译，不含业务逻辑（backend-engineer §2.1）。对齐 API-ENDPOINTS §14.5
 * F-5020 建议端点 {@code DELETE /api/user/self}（self-scope 注销，按 repo 实际承载）。</p>
 *
 * <p>self-scope 鉴权（只能注销本人）：注销目标恒取自会话内 {@link AuthenticatedActor#userId()}，
 * <b>不接受</b>外部传入任意 userId——从根上杜绝越权注销他人账号（ROLE-PERMISSION-MATRIX §3 self-scope）。
 * 未认证时由 {@code @CurrentActor required=true} 抛 {@code AuthenticationRequiredException}（→401，
 * 由 shared 的 {@code SecurityExceptionHandler} 翻译）。</p>
 *
 * <p>领域/业务异常（用户不存在 → 404）由 {@link ComplianceExceptionHandler} 统一翻译。</p>
 */
@RestController
@RequestMapping("/api/user")
public class AccountDeactivationController {

    private final DeactivateAccountUseCase deactivateAccountUseCase;

    /**
     * @param deactivateAccountUseCase 账号注销用例（应用层）
     */
    public AccountDeactivationController(DeactivateAccountUseCase deactivateAccountUseCase) {
        this.deactivateAccountUseCase = deactivateAccountUseCase;
    }

    /**
     * 注销本人账号（F-5020）。
     *
     * <p>对齐 {@code DELETE /api/user/self}。注销目标 = 当前会话操作者本人，触发：PII 匿名化 +
     * 令牌/OAuth 绑定/passkey 级联删除 + 日志归属匿名化（全程单事务，失败整体回滚）。
     * 成功回执仅含处置条数（客户视图零敏感泄露）。</p>
     *
     * @param actor 当前认证操作者（注销目标恒为本人，self-scope）
     * @return 成功信封，data 为注销回执（各类数据处置条数）
     */
    @DeleteMapping("/self")
    public ApiResponse<AccountDeactivationView> deactivateSelf(@CurrentActor AuthenticatedActor actor) {
        // 协议翻译：会话本人 id → 注销命令。不接受外部 userId，self-scope 由此从根保证。
        DeactivateAccountCommand command = new DeactivateAccountCommand(actor.userId());
        AccountDeactivationCascade.CascadeResult result = deactivateAccountUseCase.deactivate(command);
        return ApiResponse.okData(AccountDeactivationView.from(result));
    }
}
