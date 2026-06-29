package com.nexa.domain.compliance.exception;

import com.nexa.common.kernel.DomainException;

/**
 * 同意闸门未通过异常（F-5021，DC-010）。
 *
 * <p>用户尚未接受「含数据出境与 prompt 留存条款的隐私政策 / 用户协议」即发起需同意的调用时抛出。
 * 领域规则来源：API-ENDPOINTS §14.5 F-5021「未接受含出境与留存条款的协议不可调用」、
 * Compliance 验收「未同意协议拒绝调用」。接口层映射为 403（拒绝调用、引导先同意）。</p>
 */
public final class ConsentRequiredException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "CONSENT_REQUIRED";

    /**
     * 构造同意闸门未通过异常。
     *
     * @param message 面向用户的稳定提示（不泄露内部条款版本细节，仅说明需先同意）
     */
    public ConsentRequiredException(String message) {
        super(CODE, message);
    }
}
