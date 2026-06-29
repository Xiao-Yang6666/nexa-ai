package com.nexa.domain.ops.setup;

import com.nexa.domain.ops.exception.InvalidSetupRequestException;

/**
 * 系统初始化提交（值对象，封装 F-4016 入参校验规则，充血、零框架依赖）。
 *
 * <p>承载首次部署引导提交的合法 root 凭据 + 模式开关，构造期即守护全部领域不变量
 * （backend-engineer §2.2 充血）：用户名长度、密码长度、两次密码一致。一旦构造成功即代表
 * 「一组合法的初始化参数」，下游用例无需再校验。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §9.1 POST /api/setup：
 * {@code username validate:max=12}、{@code password validate:min=8}、需与 confirm_password 一致。</p>
 */
public final class SetupSubmission {

    /** 用户名最大长度（F-4016 validate:max=12）。 */
    private static final int USERNAME_MAX = 12;
    /** 密码最小长度（F-4016 validate:min=8）。 */
    private static final int PASSWORD_MIN = 8;

    private final String username;
    private final String rawPassword;
    private final boolean selfUseModeEnabled;
    private final boolean demoSiteEnabled;

    private SetupSubmission(String username, String rawPassword,
                            boolean selfUseModeEnabled, boolean demoSiteEnabled) {
        this.username = username;
        this.rawPassword = rawPassword;
        this.selfUseModeEnabled = selfUseModeEnabled;
        this.demoSiteEnabled = demoSiteEnabled;
    }

    /**
     * 校验并构造初始化提交（F-4016 全部入参规则）。
     *
     * @param username        root 用户名（必填，≤12）
     * @param password        密码（必填，≥8）
     * @param confirmPassword 确认密码（须与 password 一致）
     * @param selfUseMode     自用模式开关
     * @param demoSite        演示模式开关
     * @return 合法的初始化提交
     * @throws InvalidSetupRequestException 任一校验失败（→400）
     */
    public static SetupSubmission create(String username, String password, String confirmPassword,
                                         boolean selfUseMode, boolean demoSite) {
        if (username == null || username.isBlank()) {
            throw new InvalidSetupRequestException("用户名不能为空");
        }
        String trimmedUsername = username.trim();
        if (trimmedUsername.length() > USERNAME_MAX) {
            throw new InvalidSetupRequestException("用户名长度不能超过 " + USERNAME_MAX + " 个字符");
        }
        if (password == null || password.length() < PASSWORD_MIN) {
            throw new InvalidSetupRequestException("密码长度不能少于 " + PASSWORD_MIN + " 个字符");
        }
        if (!password.equals(confirmPassword)) {
            // 两次不一致是常见用户错误，明确提示但不回显密码本身。
            throw new InvalidSetupRequestException("两次输入的密码不一致");
        }
        return new SetupSubmission(trimmedUsername, password, selfUseMode, demoSite);
    }

    /** @return root 用户名（已校验、去空白） */
    public String username() {
        return username;
    }

    /** @return 明文密码（仅供基础设施层哈希用，绝不进任何视图/日志） */
    public String rawPassword() {
        return rawPassword;
    }

    /** @return 自用模式开关 */
    public boolean selfUseModeEnabled() {
        return selfUseModeEnabled;
    }

    /** @return 演示模式开关 */
    public boolean demoSiteEnabled() {
        return demoSiteEnabled;
    }
}
