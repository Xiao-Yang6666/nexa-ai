package com.nexa.interfaces.api.model;

import com.nexa.common.kernel.DomainException;

import com.nexa.domain.model.exception.AliasCrossScopeException;
import com.nexa.domain.model.exception.InvalidModelParameterException;
import com.nexa.domain.model.exception.ModelMetaNotFoundException;
import com.nexa.domain.model.exception.PublicModelNotFoundException;
import com.nexa.domain.model.exception.UpstreamSyncException;
import com.nexa.domain.model.exception.UserModelAliasNotFoundException;
import com.nexa.domain.model.exception.VendorNotFoundException;
import com.nexa.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 模型/供应商管理接口层异常处理（协议翻译：领域/集成异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码，
 * 用例/控制器因此不写 try/catch 模板代码（backend-engineer §3.2）。仅对模型/供应商/用户模型控制器
 * 生效（{@code assignableTypes}），不影响其他 bounded context。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidModelParameterException} → 400（缺失/非法入参、重名、用户不存在）</li>
 *   <li>{@link ModelMetaNotFoundException} / {@link VendorNotFoundException} → 404</li>
 *   <li>{@link UpstreamSyncException} → 502（上游同步集成故障）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice(assignableTypes = {
        ModelController.class, VendorController.class, UserModelController.class,
        PublicModelController.class, UserModelAliasController.class})
public class ModelExceptionHandler {

    /**
     * 模型/供应商入参非法 → 400（含重名、缺 id、用户不存在）。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidModelParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidModelParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 模型不存在 → 404。
     *
     * @param e 模型缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(ModelMetaNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleModelNotFound(ModelMetaNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 供应商不存在 → 404。
     *
     * @param e 供应商缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(VendorNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVendorNotFound(VendorNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 对外模型不存在 → 404（F-6001）。
     *
     * @param e 对外模型缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(PublicModelNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicModelNotFound(PublicModelNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 自助映射不存在 → 404（F-6003）。
     *
     * @param e 自助映射缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(UserModelAliasNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAliasNotFound(UserModelAliasNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 自助映射跨 scope 越权写 → 403（F-6003 self-scope 护栏）。
     *
     * <p>不回显目标归属细节（避免越权探测），统一稳定提示（安全默认）。</p>
     *
     * @param e 越权异常
     * @return 403 错误信封
     */
    @ExceptionHandler(AliasCrossScopeException.class)
    public ResponseEntity<ApiResponse<Void>> handleCrossScope(AliasCrossScopeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 上游同步集成失败 → 502。
     *
     * <p>用 502 Bad Gateway 表达「本服务正常但上游故障」，区别于 4xx 客户端错误。message 不含上游凭证。</p>
     *
     * @param e 上游同步异常
     * @return 502 错误信封
     */
    @ExceptionHandler(UpstreamSyncException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(UpstreamSyncException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }
}
