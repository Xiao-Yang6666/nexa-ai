package com.nexa.application.sensitiveverify;

/**
 * 敏感动作二次验证命令（应用层入参，F-1038）。
 *
 * <p>承载从 openapi {@code POST /api/verify} 不定结构请求体中抽取的三类可选凭据。三者皆可空——
 * F-1038 规则是「任一通过即放行」，用户只需提交其中一种因子（或多种，任一命中即可）。
 * 命令是不可变载体，不含业务逻辑（编排在 {@link VerifySensitiveActionUseCase}）。</p>
 *
 * <p>安全声明：{@code password} 为明文，仅用于即时比对，<b>绝不</b>记录/持久化（沿用账号域 RawPassword 约定）。</p>
 *
 * @param userId                会话用户 id（来自鉴权主体）
 * @param password              提交的明文密码，可空（不验密码因子时为 null/空）
 * @param totpCode              提交的 TOTP 口令，可空
 * @param passkeyAssertionJson  提交的 passkey 断言响应 JSON，可空
 */
public record VerifySensitiveActionCommand(long userId,
                                           String password,
                                           String totpCode,
                                           String passkeyAssertionJson) {

    /**
     * 是否提交了密码因子（非空白）。
     *
     * @return 提交了非空密码返回 {@code true}
     */
    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    /**
     * 是否提交了 TOTP 因子（非空白）。
     *
     * @return 提交了非空 TOTP 口令返回 {@code true}
     */
    public boolean hasTotp() {
        return totpCode != null && !totpCode.isBlank();
    }

    /**
     * 是否提交了 passkey 断言因子（非空白）。
     *
     * @return 提交了非空断言 JSON 返回 {@code true}
     */
    public boolean hasPasskey() {
        return passkeyAssertionJson != null && !passkeyAssertionJson.isBlank();
    }
}
