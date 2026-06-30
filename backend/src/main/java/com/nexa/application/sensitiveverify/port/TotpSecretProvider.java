package com.nexa.application.sensitiveverify.port;

import java.util.Optional;

/**
 * 用户 TOTP 密钥来源端口（应用层定义，基础设施层实现，F-1038）。
 *
 * <p>把「按 userId 取本人已启用的 TOTP 密钥」这一职责与「TOTP 算法校验」解耦：算法在
 * {@code com.nexa.domain.account.twofa.vo.TotpVerifier}（已就绪、纯算法），而密钥的存取依赖 twofa 限界上下文的
 * 持久化（聚合/仓储，属<b>后续 wave</b>落地）。本端口即该依赖的抽象。</p>
 *
 * <p>DDD 依赖倒置：TOTP 校验适配器只依赖本端口，twofa 持久化 wave 落地后提供真实实现即可，
 * 不动校验逻辑（backend-engineer §2.3）。当前 wave 提供\"未就绪\"桩实现（始终返回空 → 视为未启用 2FA）。</p>
 */
public interface TotpSecretProvider {

    /**
     * 取某用户已启用的 TOTP 共享密钥（Base32 串）。
     *
     * @param userId 会话用户 id
     * @return 用户已启用 2FA 时返回其密钥 Base32 串；未启用 / 无密钥返回 {@link Optional#empty()}
     */
    Optional<String> findEnabledSecret(long userId);
}
