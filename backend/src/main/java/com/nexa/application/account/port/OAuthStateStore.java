package com.nexa.application.account.port;

import com.nexa.domain.account.vo.OAuthState;

import java.util.Optional;

/**
 * OAuth state 暂存端口（应用层定义，基础设施层用内存/Redis 实现）。
 *
 * <p>承载 F-1015 的 state 存取与时效：发起授权前 {@link #save} 暂存 state（含 token + aff），
 * 回调时 {@link #consume} 按 token 取回并<b>一次性消费</b>（取出即删，防重放）。state 不存在/
 * 已过期/已被消费 → 返回空，用例据此判 CSRF 失败（{@code InvalidOAuthStateException}）。</p>
 *
 * <p>定义为端口以便用例不依赖具体存储：本切片基础设施层先用带 TTL 的内存实现；
 * 多实例部署需共享 state 时换 Redis 实现，用例与接口层无需改动（backend-engineer §2.3）。</p>
 */
public interface OAuthStateStore {

    /**
     * 暂存一个 state（F-1015 生成 state 时调用）。
     *
     * <p>实现应记录创建时刻以支持 TTL 过期；token 为存取键。</p>
     *
     * @param state 待暂存的 state 值对象
     */
    void save(OAuthState state);

    /**
     * 按 token 取回并一次性消费 state（回调校验 CSRF 时调用）。
     *
     * <p>命中且未过期 → 返回 state 并<b>从存储删除</b>（一次性，防回放）；不存在/过期 → 空。</p>
     *
     * @param token 回调带回的 state token
     * @return 命中且有效返回 state，否则空
     */
    Optional<OAuthState> consume(String token);
}
