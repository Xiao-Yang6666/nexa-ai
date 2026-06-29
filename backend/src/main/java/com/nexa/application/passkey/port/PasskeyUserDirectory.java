package com.nexa.application.passkey.port;

/**
 * 账号目录端口（passkey 应用层依赖，基础设施层桥接账号域 {@code UserRepository} 实现）。
 *
 * <p>passkey 限界上下文在 ceremony 中需要用户的少量身份信息，但<b>不应直接依赖账号域的领域对象</b>
 * （跨 BC 耦合）。故定义本防腐层端口，只暴露 passkey 所需的最小中性投影 {@link UserSnapshot}，
 * 实现层做账号域到本端口的适配（backend-engineer §2.5 BC 解耦 / 防腐层）。</p>
 */
public interface PasskeyUserDirectory {

    /**
     * 按用户 id 取用户名（注册 begin 需要展示名）。
     *
     * @param userId 用户 id
     * @return 命中返回用户名，否则 {@code null}
     */
    String findUsernameById(long userId);

    /**
     * 按用户名取用户 id（登录 begin 在指定用户名时定位本人凭据）。
     *
     * @param username 用户名
     * @return 命中返回用户 id，否则 {@code null}
     */
    Long findUserIdByUsername(String username);

    /**
     * 按用户 id 取中性用户快照（登录断言成功后构造客户视图 {@code UserVO}）。
     *
     * @param userId 用户 id
     * @return 命中返回快照，否则 {@code null}
     */
    UserSnapshot findSnapshotById(long userId);

    /**
     * 中性用户快照（账号域到 passkey 域的防腐层投影，仅含客户视图字段，零敏感）。
     *
     * @param id          用户 id
     * @param username    用户名
     * @param role        角色编码
     * @param status      状态编码
     * @param quota       当前额度
     * @param affCode     个人邀请码
     * @param email       邮箱（可空）
     * @param lastLoginAt 最近登录时间（epoch 秒）
     */
    record UserSnapshot(
            Long id,
            String username,
            int role,
            int status,
            long quota,
            String affCode,
            String email,
            long lastLoginAt) {
    }
}
