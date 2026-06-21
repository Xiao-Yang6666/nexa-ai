package com.nexa.sensitiveverify.application.port;

/**
 * Passkey 二次验证端口（应用层定义，基础设施层实现，F-1038）。
 *
 * <p>F-1038 因子之一：用 passkey（WebAuthn）断言做二次验证。端口只声明「给定用户与 authenticator 断言响应，
 * 验签是否通过且归属本人」这一能力——具体验签复用 passkey 限界上下文已就绪的
 * {@code com.nexa.passkey.application.VerifyPasskeyUseCase}（适配器在 infrastructure 跨域桥接）。</p>
 *
 * <p>DDD 依赖倒置：用例只依赖本端口，可桩替换单测（backend-engineer §2.3）。</p>
 */
public interface PasskeyVerificationPort {

    /**
     * 校验某用户提交的 passkey 断言是否验签通过且归属本人。
     *
     * <p>实现须以<b>安全默认</b>处理边界：本人无 passkey / 断言验签失败 / 凭据非本人一律返回 {@code false}
     * （不抛异常、不区分原因），由领域服务统一裁决。</p>
     *
     * @param userId                会话用户 id
     * @param assertionResponseJson 前端回传的 authenticator 断言响应（原始 JSON）
     * @return 验签通过且归属本人返回 {@code true}，否则 {@code false}
     */
    boolean verifyPasskeyAssertion(long userId, String assertionResponseJson);
}
