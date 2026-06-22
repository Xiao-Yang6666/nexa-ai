package com.nexa.channel.interfaces.api;

import com.nexa.channel.domain.exception.ChannelModelCostNotFoundException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 供应商成本配置 / 供应渠道池接口层异常处理（协议翻译，F-6005/F-6006）。
 *
 * <p>{@link ChannelExceptionHandler} 仅绑定 {@code ChannelController}，本类补齐 F-6005/F-6006 新控制器
 * （{@link ChannelModelCostController} / {@link ChannelPoolController}）的异常映射。安全：message 不含 cost/凭证内部细节。</p>
 *
 * <p>状态码映射：
 * <ul>
 *   <li>{@link InvalidChannelParameterException} → 400（缺失/非法入参）</li>
 *   <li>{@link ChannelModelCostNotFoundException} → 404（按 id 删除但成本行缺失）</li>
 *   <li>请求体不可读（非法 JSON）→ 400</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice(assignableTypes = {
        ChannelModelCostController.class, ChannelPoolController.class})
public class ChannelCostPoolExceptionHandler {

    /**
     * 入参非法 → 400。
     *
     * @param e 参数非法异常
     * @return 400 错误信封
     */
    @ExceptionHandler(InvalidChannelParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(InvalidChannelParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 成本配置不存在 → 404。
     *
     * @param e 成本缺失异常
     * @return 404 错误信封
     */
    @ExceptionHandler(ChannelModelCostNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCostNotFound(ChannelModelCostNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }
}
