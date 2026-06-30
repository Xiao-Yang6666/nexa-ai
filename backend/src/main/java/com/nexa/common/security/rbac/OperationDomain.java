package com.nexa.common.security.rbac;

/**
 * 操作域（RBAC 矩阵的列，值对象）。
 *
 * <p>对齐 ROLE-PERMISSION-MATRIX §2「12 类操作域 O01~O12」，覆盖 17 个能力域。
 * 角色×操作域的授权判定由领域服务 {@link RbacPolicy} 给出（矩阵 §3）。</p>
 *
 * <p>领域规则来源：ROLE-PERMISSION-MATRIX §2 操作域分类表 + §6 F-5034「角色×操作权限矩阵配置化」。</p>
 */
public enum OperationDomain {

    /** O01 公开浏览/试用（公开站/价格页/广场/Playground）。 */
    O01_PUBLIC_BROWSE("O01", "公开浏览/试用"),

    /** O02 账号与身份（自身）：注册/登录/2FA/Passkey/OAuth 绑定。 */
    O02_ACCOUNT_IDENTITY("O02", "账号与身份(自身)"),

    /** O03 令牌/API Key（自身）：令牌 CRUD/取明文/白名单。 */
    O03_TOKEN_SELF("O03", "令牌/API Key(自身)"),

    /** O04 额度/充值/订阅（自身）。 */
    O04_QUOTA_SELF("O04", "额度/充值/订阅(自身)"),

    /** O05 增长（签到/邀请）。 */
    O05_GROWTH_SELF("O05", "增长(签到/邀请)"),

    /** O06 异步任务（自身）：MJ/Suno/视频。 */
    O06_ASYNC_TASK_SELF("O06", "异步任务(自身)"),

    /** O07 渠道管理（CRUD/测试/余额/多 Key/上游同步）。 */
    O07_CHANNEL_MANAGE("O07", "渠道管理"),

    /** O08 计费配置（倍率/阶梯/表达式/兑换码/订阅计划）。 */
    O08_BILLING_CONFIG("O08", "计费配置"),

    /** O09 用户管理（管理端用户 CRUD/分组/2FA 重置/封禁）。 */
    O09_USER_MANAGE("O09", "用户管理"),

    /** O10 日志/审计/用量。 */
    O10_LOG_AUDIT("O10", "日志/审计/用量"),

    /** O11 运维（性能/缓存/部署/监控）。 */
    O11_OPS("O11", "运维(性能/缓存/部署/监控)"),

    /** O12 系统设置（全站 Option/OAuth provider/setup/限流/敏感词）。 */
    O12_SYSTEM_SETTINGS("O12", "系统设置(全站)");

    private final String code;
    private final String label;

    OperationDomain(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** @return 操作域代码（如 {@code O09}） */
    public String code() {
        return code;
    }

    /** @return 中文标签（矩阵可读展示，F-5034） */
    public String label() {
        return label;
    }
}
