package com.nexa.compliance.interfaces.api.dto;

import com.nexa.compliance.application.port.AccountDeactivationCascade;

/**
 * 账号注销回执视图（客户视图，零敏感泄露，F-5020）。
 *
 * <p>注销成功后回执给本人的级联处置摘要——仅含各类数据的处置<b>条数</b>，绝不含任何 PII 或
 * 被删数据的内容/标识（产品铁律：客户视图零敏感字段）。用于前端提示「已删除 N 个令牌、解绑 M 个第三方账号…」。</p>
 *
 * @param tokensPurged        删除的 API 令牌数
 * @param oauthBindingsPurged 解绑的第三方账号数
 * @param passkeysPurged      删除的 passkey 数
 * @param twoFaPurged         失效的 2FA 配置数
 * @param logsAnonymized      匿名化的历史日志条数
 */
public record AccountDeactivationView(int tokensPurged,
                                      int oauthBindingsPurged,
                                      int passkeysPurged,
                                      int twoFaPurged,
                                      long logsAnonymized) {

    /**
     * 由应用层级联结果投影为客户视图。
     *
     * @param r 级联处置结果
     * @return 注销回执视图
     */
    public static AccountDeactivationView from(AccountDeactivationCascade.CascadeResult r) {
        return new AccountDeactivationView(
                r.tokensPurged(),
                r.oauthBindingsPurged(),
                r.passkeysPurged(),
                r.twoFaPurged(),
                r.logsAnonymized());
    }
}
