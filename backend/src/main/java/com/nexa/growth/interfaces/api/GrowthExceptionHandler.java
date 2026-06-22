package com.nexa.growth.interfaces.api;

import com.nexa.growth.domain.exception.DomainException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 增长子域（签到 + 邀请返利分销）接口层异常处理（协议翻译：领域异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@link DomainException} 子类，携带稳定业务错误码 {@code code()} 与建议
 * HTTP 状态码 {@code httpStatus()}），接口层在此集中翻译为 openapi {@code ErrorResponse}
 * （{@code {success:false, message}}）+ 对应 HTTP 状态码，用例/控制器因此不写 try/catch 模板代码
 * （backend-engineer §3.2）。此前 growth 子域漏写本处理器（S8 覆盖缺口），导致签到等领域异常透传成
 * servlet 异常 → 被 Spring Security error dispatch 误判为未认证 → 返回 403/500 空 body，而非约定的
 * 4xx 业务错误 JSON；本处理器补齐该缺口。</p>
 *
 * <p>仅对增长域两个控制器生效（{@code assignableTypes}），<b>不</b>全局抢其他 bounded context 的异常，
 * 与 account/model/relay 等子域各自的 {@code @RestControllerAdvice} 隔离（对齐每子域独立 advice 惯例）。</p>
 *
 * <p>状态码映射（直接采用领域异常自带的 {@link DomainException#httpStatus()}，由各异常类按 PRD 钉死）：
 * <ul>
 *   <li>{@code CheckinDisabledException}（签到未启用）→ 400</li>
 *   <li>{@code AlreadyCheckedInException}（今日已签）→ 400</li>
 *   <li>{@code InvalidCheckinSettingException}（配置非法 Min&gt;Max/为负）→ 400</li>
 *   <li>{@code AffQuotaTransferException}（划转非法：低于最小单位/额度不足/合规未过）→ 400</li>
 *   <li>{@code GrowthUserNotFoundException}（目标用户缺失/已删）→ 404</li>
 *   <li>{@code GrowthPersistenceException}（底层数据访问失败）→ 500（不向客户端泄露底层细节）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice(assignableTypes = {CheckinController.class, AffiliateController.class})
public class GrowthExceptionHandler {

    /**
     * 增长域领域异常统一翻译 → 领域自带的建议 HTTP 状态码 + {@code {success:false, message}} 错误信封。
     *
     * <p>统一处理 {@link DomainException} 基类（签到未启用/今日已签/配置非法/划转非法/用户缺失/持久化失败
     * 等全部子类），状态码取异常自身的 {@link DomainException#httpStatus()}（PRD 钉死、单点维护），message
     * 取用户可见的领域描述。避免逐个子类重复写 handler（同状态码同信封结构）。</p>
     *
     * @param e 增长域领域异常（携带稳定 code 与建议 HTTP status）
     * @return 对应 HTTP 状态码 + 错误信封
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException e) {
        return ResponseEntity.status(e.httpStatus()).body(ApiResponse.error(e.getMessage()));
    }
}
