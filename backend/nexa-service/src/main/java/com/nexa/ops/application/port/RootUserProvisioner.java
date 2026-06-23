package com.nexa.ops.application.port;

/**
 * Root 用户开通端口（应用层端口，F-4016 初始化创建 root）。
 *
 * <p>系统初始化提交成功后需创建一个 {@code Role=root、Quota=100000000} 的用户。该动作跨越
 * 账号 bounded context 的持久化，但系统初始化是一次性引导、不依赖账号域聚合的复杂行为，故由
 * ops 应用层定义端口、基础设施层适配实现（直接写 users 表），避免 ops 编译期耦合 account 域。</p>
 *
 * <p>依赖倒置：application 定接口、infrastructure 实现（backend-engineer §2.3）。密码哈希在
 * 适配实现内部完成（BCrypt），明文密码绝不落库、不进日志。</p>
 */
public interface RootUserProvisioner {

    /** root 用户初始额度（API-ENDPOINTS §9.1：Quota=100000000）。 */
    long ROOT_INITIAL_QUOTA = 100_000_000L;

    /**
     * 是否已存在 root 用户（F-4015 GET /api/setup 出参 {@code root_init}）。
     *
     * @return 存在 root 用户返回 {@code true}
     */
    boolean rootUserExists();

    /**
     * 创建 root 用户（F-4016 副作用之一）。
     *
     * @param username    用户名（已通过领域校验）
     * @param rawPassword 明文密码（适配实现内部 BCrypt 哈希后落库，不外泄）
     * @throws RuntimeException 持久化失败（适配实现 wrap 带上下文，不吞错）
     */
    void provisionRootUser(String username, String rawPassword);
}
