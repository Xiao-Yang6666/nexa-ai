package com.nexa.domain.ops.exception;

import com.nexa.sharedkernel.HttpAwareDomainException;

/**
 * 全站选项更新值非法（F-4018 及其附着的横切配置校验 F-4032/F-4033/F-4034/F-4035）。
 *
 * <p>领域规则来源：API-ENDPOINTS §9.2 PUT /api/option/ 逐键校验分支：
 * <ul>
 *   <li>启用 OAuth（GitHubOAuthEnabled/discord.enabled/oidc.enabled）但未填 ClientId→「请先填入...」；</li>
 *   <li>{@code theme.frontend} 非 default/classic→「无效的主题值...」（F-4035）；</li>
 *   <li>{@code ModelRequestRateLimitGroup} 非法 JSON 或非法 [count,duration]→校验失败（F-4032）；</li>
 *   <li>{@code payment_setting.compliance_*} 禁止经本接口改（走 §9.5 专用端点）。</li>
 * </ul>
 * 属客户端入参错误 → 400 Bad Request。</p>
 */
public class InvalidOptionValueException extends HttpAwareDomainException {

    /**
     * @param message 具体校验失败原因（中文可读，对齐契约文案；不含敏感值）
     */
    public InvalidOptionValueException(String message) {
        super("OPS_INVALID_OPTION", 400, message);
    }
}
