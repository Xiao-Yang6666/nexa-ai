package com.nexa.token.domain.exception;

/**
 * 令牌入参非法异常（→ 400）。
 *
 * <p>领域规则违反场景：name 为空/超长（{@code MsgTokenNameTooLong}，≤50）、remain_quota 越界
 * （[0, maxQuotaValue]）、批量取明文 key 超上限（>100）、分组名非法等（F-3001/F-3005/F-3006/
 * F-3008~F-3012）。在领域聚合/值对象构造或行为方法中抛出，由接口层翻译为 400。</p>
 */
public class InvalidTokenParameterException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_TOKEN_PARAMETER";

    /**
     * @param message 可读的参数非法描述（绝不含令牌明文 key）
     */
    public InvalidTokenParameterException(String message) {
        super(CODE, message);
    }
}
