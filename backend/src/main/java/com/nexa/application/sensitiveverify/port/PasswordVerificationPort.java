package com.nexa.application.sensitiveverify.port;

/**
 * 密码二次验证端口（应用层定义，基础设施层实现，F-1038）。
 *
 * <p>F-1038 因子之一：用账户密码做二次验证。端口只声明「给定用户与明文密码，是否匹配」这一能力，
 * 不暴露密码哈希/存储细节——具体实现复用账号域的用户仓储 + 密码哈希器（适配器在 infrastructure 跨域桥接）。</p>
 *
 * <p>DDD 依赖倒置：本上下文的用例只依赖此端口，可在单测用桩替换、无需起 DB（backend-engineer §2.3）。
 * 端口落在 application 而非 domain，是因为校验需 IO（查用户、调哈希器），不属纯领域逻辑。</p>
 */
public interface PasswordVerificationPort {

    /**
     * 校验某用户提交的明文密码是否正确。
     *
     * <p>实现须以<b>安全默认</b>处理边界：用户不存在 / 密码格式非法 / 不匹配一律返回 {@code false}
     * （不抛异常、不区分原因，防枚举），由领域服务统一裁决放行与否。</p>
     *
     * @param userId      会话用户 id
     * @param rawPassword 用户提交的明文密码（仅用于即时比对，调用方不得记录/持久化）
     * @return 匹配且账户可用返回 {@code true}，否则 {@code false}
     */
    boolean verifyPassword(long userId, String rawPassword);
}
