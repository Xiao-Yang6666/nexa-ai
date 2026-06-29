package com.nexa.domain.sensitiveverify.vo;

/**
 * 二次验证因子类型值对象（F-1038）。
 *
 * <p>枚举本系统支持的敏感动作验证因子。F-1038 规则：以下任一因子校验通过即放行受保护动作
 * （密码 / 2FA(TOTP) / passkey 三选一，不强制多因子）。本枚举仅标识「用哪种因子」，
 * 具体校验逻辑由各因子对应的应用层端口实现（domain 不做 IO）。</p>
 *
 * <p>值对象（不可变、按值相等，枚举天然满足），零框架依赖（backend-engineer §2.4）。</p>
 */
public enum VerificationMethod {

    /** 账户密码校验（比对密码哈希）。 */
    PASSWORD,

    /** 双因子 TOTP 校验（RFC 6238 一次性口令）。 */
    TOTP,

    /** Passkey（WebAuthn）断言二次验证。 */
    PASSKEY
}
