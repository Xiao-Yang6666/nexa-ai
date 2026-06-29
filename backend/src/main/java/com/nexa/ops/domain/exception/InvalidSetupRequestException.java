package com.nexa.ops.domain.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 系统初始化提交入参非法（F-4016）。
 *
 * <p>领域规则来源：API-ENDPOINTS §9.1 POST /api/setup —— {@code username>12}、{@code password<8}、
 * 两次密码不一致 等校验失败。属客户端入参错误 → 400 Bad Request。</p>
 */
public class InvalidSetupRequestException extends HttpAwareDomainException {

    /**
     * @param message 具体校验失败原因（中文可读，不含敏感值）
     */
    public InvalidSetupRequestException(String message) {
        super("OPS_INVALID_SETUP", 400, message);
    }
}
