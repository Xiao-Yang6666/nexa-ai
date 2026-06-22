package com.nexa.relay.application;

/**
 * Relay 转发鉴权上下文（应用层入参，承载已认证调用方身份 + 分组，供 {@link RelayForwardUseCase#forward}）。
 *
 * <p>领域规则来源：prd-relay RL-1（{@code TokenAuth → Distribute} 注入分组/模型）+ RL-7（按
 * {@code (Token.Group/User.Group) × B} 选渠、按 {@code GroupRatio(UsingGroup)} 计费）。本 VO 把转发链
 * 所需的调用方上下文（谁、哪个分组、哪个 token）收敛为不可变记录，由接口层从安全上下文 + token
 * 解析后构造传入。</p>
 *
 * <p><b>接线点</b>：当前接口层仅能从 {@code AuthenticatedActor} 拿到 {@code userId/username}；
 * 完整的 token 级上下文（{@code group}/{@code tokenId}/{@code tokenName}/ModelLimits/allow_ips）依赖
 * TokenAuth 中间件（key 鉴权）落地——{@code group} 暂以缺省值占位，待 REQ-06（key 减法校验）与
 * token 鉴权接线后由真实 token 注入。</p>
 *
 * @param userId    调用方用户 id（必 &gt; 0）
 * @param username  用户名（可空，仅日志/审计可读性）
 * @param group     使用分组（折扣维 + 选渠维；缺省 {@code "default"}）
 * @param tokenId   调用 token id（可空，token 鉴权接线后填充）
 * @param tokenName token 名（可空）
 */
public record RelayAuthContext(
        long userId,
        String username,
        String group,
        Long tokenId,
        String tokenName
) {

    /** 缺省分组（token 未注入分组时回退）。 */
    public static final String DEFAULT_GROUP = "default";

    public RelayAuthContext {
        if (userId <= 0) {
            throw new IllegalArgumentException("relay auth context userId must be positive, got " + userId);
        }
        if (group == null || group.isBlank()) {
            group = DEFAULT_GROUP;
        }
    }
}
