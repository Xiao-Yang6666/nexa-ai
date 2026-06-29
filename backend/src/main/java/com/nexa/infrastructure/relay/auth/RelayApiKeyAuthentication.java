package com.nexa.infrastructure.relay.auth;

import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import com.nexa.common.security.infrastructure.auth.ActorAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Relay API-Key 认证令牌（基础设施层适配，REQ-API-KEY-AUTH）。
 *
 * <p>{@code /v1/**} 中继端点的调用方用 {@code tokens.key}（形如 {@code sk-...}）鉴权，而非登录 JWT。
 * 本令牌继承 {@link ActorAuthenticationToken}，使其同样被 {@code SecurityContextActorHolder}、方法级
 * {@code @RequireRole} 拦截器与 {@code @CurrentActor} 解析器识别为「已认证操作者」（复用 RBAC USER 级门槛）；
 * 同时<b>额外承载 token 级上下文</b>（{@code tokenId}/{@code group}/{@code tokenName}），供
 * {@code RelayController} 构造完整 {@code RelayAuthContext}（REQ-03 选渠 / REQ-05 计费 / REQ-06 key 减法校验
 * 据此真实激活）。</p>
 *
 * <p>角色取 {@code COMMON}：任何合法 token 必归属一个真实用户（≥ common），relay 端点仅要求
 * {@code AuthLevel.USER}，故无需回查用户实际角色即可满足门槛（最小依赖，不跨查账号域）。</p>
 */
public class RelayApiKeyAuthentication extends ActorAuthenticationToken {

    private final long tokenId;
    private final String group;
    private final String tokenName;

    /**
     * @param actor     由 token 归属用户构造的已认证操作者（userId=token.user_id，role=COMMON）
     * @param tokenId   调用 token id（tokens.id）
     * @param group     token 使用分组（tokens.group，可空串；选渠/计费维）
     * @param tokenName token 名（tokens.name，日志/审计用）
     */
    public RelayApiKeyAuthentication(AuthenticatedActor actor, long tokenId, String group, String tokenName) {
        super(actor);
        this.tokenId = tokenId;
        this.group = group;
        this.tokenName = tokenName;
    }

    /** @return 调用 token id（tokens.id） */
    public long tokenId() {
        return tokenId;
    }

    /** @return token 使用分组（可空串，调用方按 RelayAuthContext 缺省归一） */
    public String group() {
        return group;
    }

    /** @return token 名（可空） */
    public String tokenName() {
        return tokenName;
    }

    /**
     * 取当前请求的 relay API-Key 认证令牌（仅 {@code /v1/**} 经 API-Key 鉴权时存在）。
     *
     * @return 命中返回令牌；非 API-Key 鉴权（如 session/JWT 走 /v1）或未认证返回 {@link Optional#empty()}
     */
    public static Optional<RelayApiKeyAuthentication> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof RelayApiKeyAuthentication relay && relay.isAuthenticated()) {
            return Optional.of(relay);
        }
        return Optional.empty();
    }
}
