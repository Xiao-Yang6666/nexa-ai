package com.nexa.domain.twofa.vo;

/**
 * 备份码哈希器（领域端口 / Port，F-1035）。
 *
 * <p>定义在领域层但<b>不在领域层实现</b>——领域只关心"能哈希、能校验"这一能力契约，
 * 具体算法（BCrypt 等）由基础设施层适配器实现并注入。与账号域 {@code PasswordHasher} 同构，
 * 但归属 twofa BC 独立定义（不跨 BC 复用领域端口，避免上下文耦合，backend-engineer §2.5）。
 * 基础设施实现可包装与密码同一套 BCrypt 算法。</p>
 *
 * <p>备份码以哈希落库（DB-SCHEMA §15 {@code code_hash}），明文仅在生成时一次性返回用户，
 * 此后不可逆——遗失只能重置（F-1035 重新生成使旧码失效）。</p>
 */
public interface BackupCodeHasher {

    /**
     * 对明文备份码计算单向哈希。
     *
     * @param rawCode 明文备份码
     * @return 不可逆哈希串（落库值）
     */
    String hash(String rawCode);

    /**
     * 校验明文备份码是否匹配既有哈希。
     *
     * @param rawCode 待校验明文
     * @param hashed  既有哈希（落库值）
     * @return 匹配返回 {@code true}，否则 {@code false}
     */
    boolean matches(String rawCode, String hashed);
}
