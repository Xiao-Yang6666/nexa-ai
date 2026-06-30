package com.nexa.domain.observability.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 指标维度/命名非法异常（F-5010，→500 内部，指标导出契约违反）。
 *
 * <p>RED 指标名/标签名不符合 Prometheus 命名规范、或标签值含非法字符时抛出。这是<b>内部一致性</b>
 * 错误（埋点代码 bug），不是客户输入错误——故默认 500，提示开发者修正埋点而非暴露给抓取方。</p>
 */
public final class InvalidMetricException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "OBS_INVALID_METRIC";

    /**
     * @param message 字段级错误提示
     */
    public InvalidMetricException(String message) {
        super(CODE, 500, message);
    }
}
