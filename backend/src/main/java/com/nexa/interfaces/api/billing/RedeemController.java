package com.nexa.interfaces.api.billing;

import com.nexa.application.billing.RedeemCodeUseCase;
import com.nexa.domain.billing.vo.Quota;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.billing.dto.RedeemRequest;
import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.rbac.AuthenticatedActor;
import com.nexa.common.security.annotation.CurrentActor;
import com.nexa.common.security.annotation.RequireRole;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户兑换控制器（sessionAuth 端点，接口层，prd-billing BL-4，F-2045）。
 *
 * <p>承载登录用户兑换兑换码端点（对齐 openapi {@code POST /api/user/topup}）：用户提交兑换码 Key，
 * 经事务式校验+入账后返回入账额度。鉴权：类级 {@link RequireRole}{@code (USER)} 要求登录；兑换人
 * 取自认证主体（{@code @CurrentActor}），杜绝伪造他人入账。</p>
 *
 * <p>命名说明：openapi 路径为 {@code /api/user/topup}（沿用现网历史命名，实为「兑换码兑换」而非充值），
 * 与充值 {@code /api/topup} 区分。</p>
 */
@RestController
@RequestMapping("/api/user")
@RequireRole(AuthLevel.USER)
public class RedeemController {

    private final RedeemCodeUseCase redeemCodeUseCase;

    /**
     * @param redeemCodeUseCase 兑换码兑换用例
     */
    public RedeemController(RedeemCodeUseCase redeemCodeUseCase) {
        this.redeemCodeUseCase = redeemCodeUseCase;
    }

    /**
     * 用户兑换兑换码（F-2045，对齐 openapi {@code POST /api/user/topup}）。
     *
     * <p>码不存在/格式错→400（RedemptionInvalid），已用→400（RedemptionAlreadyUsed），
     * 过期→400（RedemptionExpired），均由 {@code BillingExceptionHandler} 翻译。成功返回入账额度
     * （openapi data {@code {quota}}）。</p>
     *
     * @param request 兑换请求（含 key）
     * @param actor   认证主体（sessionAuth 注入，提供兑换人 id）
     * @return 成功信封，data 为 {@code {quota: 入账额度}}
     */
    @PostMapping("/topup")
    public ApiResponse<Map<String, Long>> redeem(
            @RequestBody RedeemRequest request,
            @CurrentActor AuthenticatedActor actor) {

        Quota credited = redeemCodeUseCase.redeem(actor.userId(), request.key());
        return ApiResponse.okData(Map.of("quota", credited.value()));
    }
}
