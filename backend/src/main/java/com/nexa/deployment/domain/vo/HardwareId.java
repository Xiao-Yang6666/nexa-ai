package com.nexa.deployment.domain.vo;

import com.nexa.deployment.domain.exception.InvalidDeploymentParameterException;

/**
 * 硬件类型 ID 值对象（io.net 硬件规格标识，F-3051）。
 *
 * <p>不可变、按值相等。守护「可用副本查询」的入参不变量（API-ENDPOINTS §10.3
 * GET /api/deployments/replicas）：</p>
 * <ul>
 *   <li>缺失（null）→「hardware_id parameter is required」</li>
 *   <li>非法或 &lt;=0 →「invalid hardware_id parameter」</li>
 * </ul>
 *
 * <p>用值对象而非裸 int 散落：把「必填 + 正数」这条契约规则固化在构造点，杜绝越界值进入
 * 用例/上游调用（backend-engineer §2.4 值对象守护不变量）。</p>
 *
 * @param value 硬件类型 ID（保证 &gt; 0）
 */
public record HardwareId(long value) {

    /**
     * 紧凑构造器：构造即校验取值大于 0。
     *
     * @throws InvalidDeploymentParameterException 当 value &lt;= 0
     */
    public HardwareId {
        if (value <= 0) {
            throw new InvalidDeploymentParameterException("invalid hardware_id parameter");
        }
    }

    /**
     * 从 query 原始字符串解析硬件 ID，按契约区分「缺失」与「非法」两类错误。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.3 F-3051 错误码段。缺失（null/空白）与非法（非数字/&lt;=0）
     * 文案不同，故在此分流；非数字归入「invalid」而非抛 NumberFormatException 泄露内部细节。</p>
     *
     * @param raw query 参数 {@code hardware_id} 原始值（可空）
     * @return 校验通过的硬件 ID
     * @throws InvalidDeploymentParameterException 缺失→required；非数字或 &lt;=0→invalid
     */
    public static HardwareId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDeploymentParameterException("hardware_id parameter is required");
        }
        long parsed;
        try {
            parsed = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            // 非数字脏值归入「invalid」（不向客户端泄露 Java 解析异常细节）。
            throw new InvalidDeploymentParameterException("invalid hardware_id parameter");
        }
        return new HardwareId(parsed);
    }
}
