package com.nexa.domain.ops.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 性能监控 / 日志清理运维入参非法（F-4022/F-4023）。
 *
 * <p>领域规则来源：API-ENDPOINTS §9.3 DELETE /api/performance/logs：
 * {@code mode} 非法→「invalid mode」；{@code value<1}→「invalid value」；
 * {@code LogDir} 未配置→报错（运维前置条件不满足）。属客户端入参/前置条件错误 → 400。</p>
 */
public class InvalidMaintenanceRequestException extends HttpAwareDomainException {

    /**
     * @param message 具体校验失败原因（对齐契约文案）
     */
    public InvalidMaintenanceRequestException(String message) {
        super("OPS_INVALID_MAINTENANCE", 400, message);
    }
}
