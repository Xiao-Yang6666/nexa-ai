package com.nexa.interfaces.growth.api;

import com.nexa.application.growth.GetAffCodeUseCase;
import com.nexa.application.growth.QueryAffiliateStatsUseCase;
import com.nexa.application.growth.TransferAffQuotaUseCase;
import com.nexa.domain.growth.model.AffiliateAccount;
import com.nexa.interfaces.growth.api.dto.AffTransferRequest;
import com.nexa.interfaces.growth.api.dto.AffiliateStatsVO;
import com.nexa.shared.web.ApiResponse;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邀请返利分销控制器（接口层，PRD GR-4/GR-5，F-1039/F-1044/F-1045）。
 *
 * <p>承载邀请返利端点（对齐 openapi）：
 * <ul>
 *   <li>{@code GET  /api/user/self/aff}          获取个人邀请码（sessionAuth + USER，F-1039）</li>
 *   <li>{@code GET  /api/user/self/aff_stats}    邀请统计三项（sessionAuth + USER，F-1045，GR-5 §5 self 视图补充端点）</li>
 *   <li>{@code POST /api/user/self/aff_transfer} 邀请额度划转为可用额度（sessionAuth + USER，F-1044）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（注入认证主体 → 调用例 → 裁剪视图），无业务逻辑。返利入账（GR-4
 * I13/I14）由领域事件订阅者 {@code InviterRewardOnUserRegistered} 在注册回调时自动触发，不在本控制器
 * 暴露（被邀请人注册即驱动，无独立 HTTP 入口）。领域异常由 {@code GrowthExceptionHandler} 翻译。</p>
 *
 * <p><b>鉴权（安全声明）</b>：{@link RequireRole}({@link AuthLevel#USER})——均为登录用户 self-scope；
 * 用户 id 由 {@code @CurrentActor} 注入，<b>不</b>从请求参数读 user_id（GR-5 §2 复用 self-scope，仅操作本人）。</p>
 */
@RestController
@RequestMapping("/api/user/self")
@RequireRole(AuthLevel.USER)
public class AffiliateController {

    private final GetAffCodeUseCase getAffCodeUseCase;
    private final QueryAffiliateStatsUseCase queryAffiliateStatsUseCase;
    private final TransferAffQuotaUseCase transferAffQuotaUseCase;

    /**
     * @param getAffCodeUseCase          取邀请码用例
     * @param queryAffiliateStatsUseCase 邀请统计用例
     * @param transferAffQuotaUseCase    邀请额度划转用例
     */
    public AffiliateController(GetAffCodeUseCase getAffCodeUseCase,
                               QueryAffiliateStatsUseCase queryAffiliateStatsUseCase,
                               TransferAffQuotaUseCase transferAffQuotaUseCase) {
        this.getAffCodeUseCase = getAffCodeUseCase;
        this.queryAffiliateStatsUseCase = queryAffiliateStatsUseCase;
        this.transferAffQuotaUseCase = transferAffQuotaUseCase;
    }

    /**
     * 获取个人邀请码（F-1039）。无则生成唯一 4 位码并落库，再次进入返回同一码（GR-4 I1/I2/I3）。
     *
     * @param actor 认证主体（注入，self-scope）
     * @return 个人邀请码字符串
     */
    @GetMapping("/aff")
    public ApiResponse<String> affCode(@CurrentActor AuthenticatedActor actor) {
        return ApiResponse.okData(getAffCodeUseCase.getOrCreate(actor.userId()));
    }

    /**
     * 邀请统计三项（F-1045）：邀请人数 / 当前可划转邀请额度 / 历史累计邀请额度（GR-5 §5）。
     *
     * <p>openapi 将三项纳入 {@code GET /api/user/self} 个人信息返回；本端点为增长域 self-scope 的等价
     * 补充入口，供前端邀请页直接取数（不污染账号域 self 视图）。</p>
     *
     * @param actor 认证主体（注入，self-scope）
     * @return 邀请统计客户视图（aff_count/aff_quota/aff_history_quota）
     */
    @GetMapping("/aff_stats")
    public ApiResponse<AffiliateStatsVO> affStats(@CurrentActor AuthenticatedActor actor) {
        AffiliateAccount account = queryAffiliateStatsUseCase.query(actor.userId());
        return ApiResponse.okData(AffiliateStatsVO.from(account));
    }

    /**
     * 邀请额度划转为可用额度（F-1044）。双重前置校验 + 合规校验，原子执行（GR-5）。
     *
     * @param actor   认证主体（注入，self-scope）
     * @param request 划转请求（quota）
     * @return 划转成功提示
     */
    @PostMapping("/aff_transfer")
    public ApiResponse<Void> affTransfer(@CurrentActor AuthenticatedActor actor,
                                         @RequestBody AffTransferRequest request) {
        transferAffQuotaUseCase.transfer(actor.userId(), request.quotaOrZero());
        return ApiResponse.ok("划转成功");
    }
}
