package com.nexa.domain.account.vo;

import com.nexa.domain.account.exception.InvalidCredentialException;

/**
 * 密码哈希器（领域端口 / Port）。
 *
 * <p>定义在领域层但<b>不在领域层实现</b>——领域只关心"能哈希、能校验"这一能力契约，
 * 具体算法（BCrypt 等）由基础设施层适配器实现并注入。这样 domain 层不依赖
 * Spring Security / 任何加密库，保持纯业务、可单测（测试用桩实现即可）。</p>
 *
 * <p>设计依据：backend-engineer §2.3 repository/port 接口在 domain 定义、infra 实现；
 * §2.1 domain 绝不 import 框架。</p>
 */
public interface PasswordHasher {

    /**
     * 对明文密码计算单向哈希。
     *
     * @param rawPassword 明文密码（已通过 {@link RawPassword} 长度校验）
     * @return 不可逆的密码哈希串（落库值）
     */
    String hash(String rawPassword);

    /**
     * 校验明文密码是否匹配既有哈希。
     *
     * @param rawPassword 待校验明文
     * @param hashed      既有哈希（落库值）
     * @return 匹配返回 {@code true}，否则 {@code false}
     */
    boolean matches(String rawPassword, String hashed);
}
