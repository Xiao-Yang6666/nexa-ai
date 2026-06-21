package com.nexa.sensitiveverify.application.port;

/**
 * TOTP 二次验证端口（应用层定义，基础设施层实现，F-1038）。
 *
 * <p>F-1038 因子之一：用双因子 TOTP 一次性口令做二次验证。端口只声明「给定用户与提交口令，是否在容忍窗口内有效」
 * 这一能力，不暴露密钥存储细节——TOTP 算法见 {@code com.nexa.twofa.domain.vo.TotpVerifier}（纯算法已就绪），
 * 但「按 userId 取本人 TOTP 密钥」依赖 twofa 限界上下文的持久化（聚合/仓储，属<b>后续 wave</b>落地）。</p>
 *
 * <p>DDD 依赖倒置：用例只依赖本端口，可桩替换单测（backend-engineer §2.3）。当前 wave 提供桩实现
 * （twofa 持久化未就绪前一律视为未启用/未通过，安全默认），twofa 持久化 wave 落地后替换为真实适配器。</p>
 */
public interface TotpVerificationPort {

    /**
     * 校验某用户提交的 TOTP 口令是否有效。
     *
     * <p>实现须以<b>安全默认</b>处理边界：用户未启用 2FA / 无密钥 / 口令格式非法 / 不在容忍窗口一律返回
     * {@code false}（不抛异常、不区分原因），由领域服务统一裁决。</p>
     *
     * @param userId 会话用户 id
     * @param code   用户提交的 TOTP 口令（数字串）
     * @return 口令在容忍窗口内有效返回 {@code true}，否则 {@code false}
     */
    boolean verifyTotp(long userId, String code);
}
