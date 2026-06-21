package com.nexa.playground.infrastructure.user;

import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.playground.application.port.PlaygroundUserGroupPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Playground 用户分组查询端口的实现（基础设施层适配器，F-4038）。
 *
 * <p>跨 bounded context 只读集成：Playground 经 {@link PlaygroundUserGroupPort} 端口声明「按 user_id
 * 取分组」的最小能力，本 adapter 用 {@code com.nexa.account} 的 {@link UserRepository} 实现，把 User 聚合
 * 投影为分组标识字符串，不让 User 领域对象渗出端口（context 间防腐，backend-engineer §2.5）。
 * 与 {@code com.nexa.model.infrastructure.catalog.UserGroupQueryAdapter} 同构，各 context 各自持有端口/适配器。</p>
 */
@Component
public class AccountPlaygroundUserGroupAdapter implements PlaygroundUserGroupPort {

    private final UserRepository userRepository;

    /** @param userRepository 用户领域仓储（跨上下文只读集成） */
    public AccountPlaygroundUserGroupAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> groupOf(long userId) {
        return userRepository.findById(userId).map(User::group);
    }
}
