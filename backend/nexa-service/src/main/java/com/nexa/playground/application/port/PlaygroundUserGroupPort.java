package com.nexa.playground.application.port;

import java.util.Optional;

/**
 * Playground 用户分组查询端口（应用层，DDD §2.3 接口抽象）。
 *
 * <p>站内试用以用户当前 {@code UsingGroup} 构造临时令牌上下文 {@code playground-<group>}，故需「按 user_id
 * 取分组」的最小只读能力。Playground 应用层经本端口声明该能力，不直接依赖 account BC 的 User 聚合，
 * 跨 context 防腐（backend-engineer §2.5）。基础设施层提供桥接 {@code com.nexa.account} 的适配器实现。</p>
 */
public interface PlaygroundUserGroupPort {

    /**
     * 取用户当前使用分组。
     *
     * @param userId 用户 id
     * @return 分组标识；用户不存在/未设置返回 {@link Optional#empty()}
     */
    Optional<String> groupOf(long userId);
}
