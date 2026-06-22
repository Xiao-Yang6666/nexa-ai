package com.nexa.routing.interfaces.api;

import com.nexa.shared.kernel.DomainException;

import com.nexa.routing.domain.exception.AffinityPersistenceException;
import com.nexa.routing.domain.exception.AutoGroupsNotEnabledException;
import com.nexa.routing.domain.exception.InvalidAffinityParameterException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 渠道亲和缓存运维接口层异常处理（协议翻译：领域/集成异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此集中翻译
 * 为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码
 * （backend-engineer §3.2）。仅对 {@link AffinityCacheController} 生效（{@code assignableTypes}），
 * 不影响其他 bounded context 的异常处理。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidAffinityParameterException} → 400（清空缺 all/rule_name、统计缺 rule_name/key_fp 等）</li>
 *   <li>{@link AutoGroupsNotEnabledException} → 400（auto 分组未启用，请求语义不可满足）</li>
 *   <li>{@link AffinityPersistenceException} → 502（缓存/规则持久化或 JSON 编解码故障，本服务正常但数据层故障）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 *
 * <p>安全：所有 message 不回显会话键明文/凭证（会话键只以指纹形态对外）。</p>
 */
@RestControllerAdvice(assignableTypes = {AffinityCacheController.class})
public class AffinityCacheExceptionHandler {

    /**
     * 亲和运维入参非法 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidAffinityParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidAffinityParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * auto 分组未启用 → 400。
     *
     * @param e auto 分组未启用异常
     * @return 400 错误信封
     */
    @ExceptionHandler(AutoGroupsNotEnabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleAutoGroupsNotEnabled(AutoGroupsNotEnabledException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 亲和缓存/规则持久化故障 → 502（本服务正常但数据层/编解码故障，区别于 4xx 客户端错误）。
     *
     * @param e 持久化异常
     * @return 502 错误信封
     */
    @ExceptionHandler(AffinityPersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(AffinityPersistenceException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }
}
