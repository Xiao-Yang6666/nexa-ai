package com.nexa.playground.domain.vo;

import java.util.Objects;

/**
 * Playground 临时令牌上下文（值对象，F-4038）。
 *
 * <p>站内试用不持有用户真实 API token，而是按用户当前 {@code UsingGroup} 即时构造一个临时令牌上下文
 * {@code tempToken{ Name: "playground-<group>", Group: UsingGroup }}，经 {@code SetupContextForToken}
 * 注入 relay 上下文后调用 {@code Relay}，按用户<b>实际额度</b>计费（落 Log，C→A 客户视图）。</p>
 *
 * <p>不变量：{@code userId} 必为正、{@code group} 非空白。令牌名固定形如 {@code playground-<group>}
 * （现网语义，用于日志/报表识别此请求来自站内试用）。不可变、按值相等（值对象，backend-engineer §2.4）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §13 F-4038 副作用「以 {@code tempToken{ Name: "playground-<group>",
 * Group: UsingGroup }} 经 {@code SetupContextForToken} 后调 {@code Relay}」。</p>
 */
public final class TempTokenContext {

    /** 临时令牌名前缀（现网固定语义）。 */
    private static final String NAME_PREFIX = "playground-";

    private final long userId;
    private final String group;
    private final String tokenName;

    private TempTokenContext(long userId, String group, String tokenName) {
        this.userId = userId;
        this.group = group;
        this.tokenName = tokenName;
    }

    /**
     * 为指定用户 + 分组构造站内试用临时令牌上下文。
     *
     * @param userId 当前登录用户 id（必 &gt; 0）
     * @param group  用户当前使用分组 {@code UsingGroup}（非空白）
     * @return 临时令牌上下文
     * @throws IllegalArgumentException userId 非正或 group 空白
     */
    public static TempTokenContext forUser(long userId, String group) {
        if (userId <= 0) {
            throw new IllegalArgumentException("playground tempToken userId must be positive, got " + userId);
        }
        if (group == null || group.isBlank()) {
            // 分组缺失会让计费/选渠口径丢失，构造期即拒，不让脏上下文流入 relay。
            throw new IllegalArgumentException("playground tempToken group must not be blank");
        }
        String trimmed = group.trim();
        return new TempTokenContext(userId, trimmed, NAME_PREFIX + trimmed);
    }

    /** @return 用户 id */
    public long userId() {
        return userId;
    }

    /** @return 使用分组 */
    public String group() {
        return group;
    }

    /** @return 临时令牌名（{@code playground-<group>}），落 Log 的 token_name 字段 */
    public String tokenName() {
        return tokenName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TempTokenContext other)) return false;
        return userId == other.userId
                && group.equals(other.group)
                && tokenName.equals(other.tokenName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, group, tokenName);
    }

    @Override
    public String toString() {
        return "TempTokenContext{userId=" + userId + ", tokenName=" + tokenName + "}";
    }
}
