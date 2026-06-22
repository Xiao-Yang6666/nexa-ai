package com.nexa.channel.interfaces.api;

import com.nexa.shared.kernel.DomainException;

import com.nexa.channel.domain.exception.ChannelNotFoundException;
import com.nexa.channel.domain.exception.ChannelOperationNotSupportedException;
import com.nexa.channel.domain.exception.ChannelUpstreamException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 渠道管理接口层异常处理（协议翻译：领域/集成异常 → HTTP 状态码 + 错误信封）。
 *
 * <p>DDD 铁律：领域抛业务语义异常（{@code DomainException} 子类，携带稳定 code），接口层在此集中
 * 翻译为 openapi {@code ErrorResponse}（{@code {success:false, message}}）+ 合适的 HTTP 状态码，
 * 用例/控制器因此不写 try/catch 模板代码（backend-engineer §3.2）。仅对 {@link ChannelController}
 * 生效（{@code assignableTypes}），不影响其他 bounded context 的异常处理。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidChannelParameterException} → 400（缺失/非法入参）</li>
 *   <li>{@link ChannelOperationNotSupportedException} → 400（操作不适用：异步渠道测试/非 Ollama 管理）</li>
 *   <li>{@link ChannelNotFoundException} → 404（按 id 操作但渠道缺失）</li>
 *   <li>{@link ChannelUpstreamException} → 502（上游测试/余额/探测/Ollama 故障）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 *
 * <p>安全：所有 message 不回显渠道 key 等敏感凭证（凭证从不进异常 message）。</p>
 */
@RestControllerAdvice(assignableTypes = {ChannelController.class})
public class ChannelExceptionHandler {

    /**
     * 渠道入参非法 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidChannelParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidChannelParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 渠道操作不被支持 → 400（异步渠道不可同步测、非 Ollama 渠道不可 Ollama 管理）。
     *
     * @param e 操作不支持异常
     * @return 400 错误信封
     */
    @ExceptionHandler(ChannelOperationNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotSupported(ChannelOperationNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 渠道不存在 → 404。
     *
     * @param e 渠道缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(ChannelNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ChannelNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 渠道上游集成失败 → 502（测试/余额/探测/Ollama 上游故障）。
     *
     * <p>用 502 Bad Gateway 表达「本服务正常但上游故障」，区别于 4xx 客户端错误。message 不含凭证。</p>
     *
     * @param e 上游集成异常
     * @return 502 错误信封
     */
    @ExceptionHandler(ChannelUpstreamException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(ChannelUpstreamException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }
}
