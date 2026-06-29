package com.nexa.interfaces.ops.api;

import com.nexa.application.ops.compliance.ConfirmPaymentComplianceUseCase;
import com.nexa.application.ops.option.ListOptionsUseCase;
import com.nexa.application.ops.option.UpdateOptionUseCase;
import com.nexa.domain.ops.compliance.PaymentComplianceConfirmation;
import com.nexa.domain.ops.option.Option;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.ops.api.dto.OptionUpdateRequest;
import com.nexa.interfaces.ops.api.dto.OptionVO;
import com.nexa.interfaces.ops.api.dto.PaymentComplianceRequest;
import com.nexa.interfaces.ops.api.dto.PaymentComplianceVO;
import com.nexa.common.security.domain.rbac.AuthLevel;
import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import com.nexa.common.security.interfaces.annotation.CurrentActor;
import com.nexa.common.security.interfaces.annotation.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 全站选项配置控制器（RootAuth 端点，接口层，F-4017/F-4018/F-4030）。
 *
 * <p>承载全站系统配置的 root 管理端点（对齐 openapi /api/option*）：
 * <ul>
 *   <li>{@code GET  /api/option/} 全站选项列表查询（剔除敏感键 + 追加 CompletionRatioMeta，F-4017）</li>
 *   <li>{@code PUT  /api/option/} 单键覆盖式更新（逐键领域校验，F-4018）</li>
 *   <li>{@code POST /api/option/payment_compliance} 支付合规声明确认（仅 dashboard 会话，F-4030）</li>
 * </ul>
 * </p>
 *
 * <p><b>鉴权（安全声明）</b>：契约三端点均 {@code rootAuth}（全站系统设置仅 root 可改）。类级
 * {@link RequireRole}({@link AuthLevel#ROOT}) 由 {@code RequireRoleInterceptor} 统一拦截，未达 root → 403、
 * 未认证 → 401（{@code SecurityExceptionHandler} 兜底）。{@link CurrentActor} 注入操作者用于合规审计署名。</p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参→调用例→裁剪视图）。校验规则全在领域
 * （{@code OptionRegistry} 主题/限流分组/合规键禁改；{@code PaymentComplianceConfirmation} 合规护栏），
 * 领域异常由 {@code OpsExceptionHandler} 翻译为 400/403/409。</p>
 *
 * <p><b>客户视图铁律</b>：F-4017 列表已在用例层剔除敏感键（{@code Option.isSensitive()}），值不外泄；
 * F-4030 出参仅回显 terms_version + confirmed_by（{@link PaymentComplianceVO} 剔除 confirmed_ip）。</p>
 */
@RestController
@RequestMapping("/api/option")
@RequireRole(AuthLevel.ROOT)
public class OptionController {

    /** Bearer 前缀（与 JwtAuthenticationFilter 同口径，用于识别 access token 凭据，F-4030 禁用）。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** F-4017 追加的补全倍率元信息派生键名（契约要求列表额外含该项）。 */
    private static final String COMPLETION_RATIO_META_KEY = "CompletionRatioMeta";

    /** 补全倍率源选项键（CompletionRatioMeta 由其当前值派生回显，未设置时为空串）。 */
    private static final String COMPLETION_RATIO_KEY = "CompletionRatio";

    private final ListOptionsUseCase listOptionsUseCase;
    private final UpdateOptionUseCase updateOptionUseCase;
    private final ConfirmPaymentComplianceUseCase confirmPaymentComplianceUseCase;

    /**
     * @param listOptionsUseCase              选项列表查询用例
     * @param updateOptionUseCase             选项更新用例
     * @param confirmPaymentComplianceUseCase 支付合规确认用例
     */
    public OptionController(ListOptionsUseCase listOptionsUseCase,
                            UpdateOptionUseCase updateOptionUseCase,
                            ConfirmPaymentComplianceUseCase confirmPaymentComplianceUseCase) {
        this.listOptionsUseCase = listOptionsUseCase;
        this.updateOptionUseCase = updateOptionUseCase;
        this.confirmPaymentComplianceUseCase = confirmPaymentComplianceUseCase;
    }

    /**
     * 全站选项列表查询（F-4017，RootAuth）。
     *
     * <p>用例已剔除敏感键（以 Token/Secret/Key/secret/api_key 结尾，值不下发）。本方法额外追加一项
     * {@code CompletionRatioMeta}（契约要求），其值取当前 {@code CompletionRatio} 选项值派生回显，
     * 供前端渲染补全倍率元信息；该派生项不参与写回（PUT 仅接受真实键）。</p>
     *
     * @return {@code data = [{key, value}, ...]}（含 CompletionRatioMeta，已剔除敏感键）
     */
    @GetMapping("/")
    public ApiResponse<List<OptionVO>> list() {
        List<Option> options = listOptionsUseCase.execute();
        List<OptionVO> views = new ArrayList<>(options.size() + 1);
        String completionRatioValue = "";
        for (Option option : options) {
            views.add(OptionVO.from(option));
            // 收集补全倍率原值，用于派生 CompletionRatioMeta（CompletionRatio 自身仍如实出现在列表）。
            if (COMPLETION_RATIO_KEY.equals(option.keyName()) && option.value() != null) {
                completionRatioValue = option.value();
            }
        }
        // 追加派生元信息项（F-4017「额外含 CompletionRatioMeta」）：当前为补全倍率原值的回显占位，
        // 待计费 BC 提供完整倍率元数据后在此接入，不在 ops 层重造计费逻辑。
        views.add(new OptionVO(COMPLETION_RATIO_META_KEY, completionRatioValue));
        return ApiResponse.okData(views);
    }

    /**
     * 全站选项更新（F-4018，RootAuth，含 F-4032/F-4035 横切校验）。
     *
     * <p>领域校验（主题白名单 / 限流分组结构 / 合规键禁改）在 {@code OptionRegistry}（用例内调用），
     * 校验失败抛 {@code InvalidOptionValueException}→400。审计仅记 key 不记 value（F-4011）。</p>
     *
     * @param request 单键更新请求 {@code {key, value}}
     * @return {@code {success:true, message:"更新成功"}}
     */
    @PutMapping("/")
    public ApiResponse<Void> update(@RequestBody OptionUpdateRequest request) {
        updateOptionUseCase.execute(request.key(), request.value());
        return ApiResponse.ok("更新成功");
    }

    /**
     * 支付合规声明确认（F-4030，RootAuth 且仅 dashboard 会话）。
     *
     * <p>会话上下文护栏：携带 {@code Authorization: Bearer}（access_token / API token）→ 领域抛
     * {@code PaymentComplianceException.requiresDashboardSession()}→403「requires dashboard session」；
     * 否则视为 dashboard 会话（session cookie，与 PlaygroundController/JwtAuthenticationFilter 同口径）。
     * {@code confirmed=false} → 400「请确认合规声明」。成功落 5 项 {@code payment_setting.compliance_*}。</p>
     *
     * @param body     合规确认请求 {@code {confirmed}}
     * @param request  HTTP 请求（用于识别凭据来源 access token vs dashboard session）
     * @param operator 当前认证操作者（@CurrentActor 注入，缺失→401；用作 confirmed_by 审计署名）
     * @return {@code data = {terms_version, confirmed_by}}（不回显 confirmed_ip）
     */
    @PostMapping("/payment_compliance")
    public ApiResponse<PaymentComplianceVO> confirmPaymentCompliance(@RequestBody PaymentComplianceRequest body,
                                                                       HttpServletRequest request,
                                                                       @CurrentActor AuthenticatedActor operator) {
        // 凭据来源识别：携带 Authorization Bearer 即 access token（非 dashboard 会话），据此让领域 403。
        boolean dashboardSession = !hasAccessToken(request);
        PaymentComplianceConfirmation confirmation = confirmPaymentComplianceUseCase.execute(
                body.confirmed(),
                dashboardSession,
                operator.username(),
                clientIp(request));
        return ApiResponse.okData(PaymentComplianceVO.from(confirmation));
    }

    /**
     * 请求是否携带 access token 凭据（Authorization: Bearer 非空）。
     *
     * @param request HTTP 请求
     * @return 携带非空 Bearer 令牌返回 {@code true}
     */
    private boolean hasAccessToken(HttpServletRequest request) {
        String authz = request.getHeader("Authorization");
        return authz != null
                && authz.startsWith(BEARER_PREFIX)
                && !authz.substring(BEARER_PREFIX.length()).isBlank();
    }

    /**
     * 解析客户端来源 IP（合规审计落 confirmed_ip，不下发客户视图）。
     *
     * <p>优先取 {@code X-Forwarded-For} 首段（反代场景真实客户端），回退 {@code RemoteAddr}。
     * 仅用于审计记录，不参与鉴权判定（可伪造，故不作安全依据）。</p>
     *
     * @param request HTTP 请求
     * @return 来源 IP（解析不到时回退 RemoteAddr）
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For 可能是逗号分隔链，首段为最初客户端。
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
