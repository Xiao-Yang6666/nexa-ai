package com.nexa.interfaces.billing.api;

import com.nexa.application.billing.CreateTopUpCommand;
import com.nexa.application.billing.CreateTopUpOrderUseCase;
import com.nexa.application.billing.CreateTopUpResult;
import com.nexa.interfaces.billing.api.dto.TopUpRequest;
import com.nexa.interfaces.billing.api.dto.TopUpUserVO;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import com.nexa.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 充值下单控制器（sessionAuth 端点，接口层，prd-billing BL-1，F-2044）。
 *
 * <p>承载登录用户发起充值下单端点（对齐 openapi {@code POST /api/topup}）：用户提交额度/金额/支付方式，
 * 经 {@link CreateTopUpOrderUseCase} 创建本地 pending 订单并调支付渠道生成收银台会话，返回跳转信息。
 * <b>不入账</b>——额度入账只在支付回调验签通过后（{@code POST /api/topup/callback/{provider}}）。</p>
 *
 * <p>鉴权：类级 {@link RequireRole}{@code (USER)} 要求登录；充值人取自认证主体（{@code @CurrentActor}），
 * 杜绝伪造他人下单。命名说明：本端点为「真实货币充值」，与兑换码兑换 {@code /api/user/topup}
 * （{@link RedeemController}，沿用现网历史命名）区分。</p>
 */
@RestController
@RequestMapping("/api/topup")
@RequireRole(AuthLevel.USER)
public class TopUpController {

    private final CreateTopUpOrderUseCase createTopUpOrderUseCase;

    /**
     * @param createTopUpOrderUseCase 充值下单用例
     */
    public TopUpController(CreateTopUpOrderUseCase createTopUpOrderUseCase) {
        this.createTopUpOrderUseCase = createTopUpOrderUseCase;
    }

    /**
     * 发起充值下单（F-2044，对齐 openapi {@code POST /api/topup}）。
     *
     * @param request 下单请求（额度/金额/支付方式/渠道）
     * @param actor   认证主体（sessionAuth 注入，提供充值人 id）
     * @return 成功信封，data 为收银台跳转信息（status=pending）
     */
    @PostMapping({"", "/"})
    public ApiResponse<TopUpUserVO> create(
            @RequestBody TopUpRequest request,
            @CurrentActor AuthenticatedActor actor) {

        CreateTopUpResult result = createTopUpOrderUseCase.create(
                actor.userId(),
                new CreateTopUpCommand(request.amount(), request.money(),
                        request.paymentMethod(), request.paymentProvider()));

        return ApiResponse.okData(new TopUpUserVO(
                result.tradeNo(), result.payUrl(), result.payParams(), result.status()));
    }
}
