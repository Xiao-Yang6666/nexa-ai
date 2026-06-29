package com.nexa.infrastructure.passkey.directory;

import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.Username;
import com.nexa.application.passkey.port.PasskeyUserDirectory;
import org.springframework.stereotype.Component;

/**
 * 账号目录端口 {@link PasskeyUserDirectory} 的实现（基础设施层防腐层适配器）。
 *
 * <p>把账号域 {@link UserRepository} 的领域对象适配为 passkey 域所需的中性投影，使 passkey 应用层
 * <b>不直接依赖账号域领域对象</b>（backend-engineer §2.5 BC 解耦）。本类是唯一允许同时 import 两个
 * BC 的接缝：account 的 {@link User}/{@link Username} 在此被翻译为 {@link PasskeyUserDirectory.UserSnapshot}。
 * 上游 passkey 用例只见端口，不感知账号域细节。</p>
 */
@Component
public class AccountPasskeyUserDirectory implements PasskeyUserDirectory {

    private final UserRepository userRepository;

    /**
     * @param userRepository 账号域用户仓储（跨 BC 注入，仅在本防腐层使用）
     */
    public AccountPasskeyUserDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** {@inheritDoc} */
    @Override
    public String findUsernameById(long userId) {
        return userRepository.findById(userId)
                .map(u -> u.username().value())
                .orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    public Long findUserIdByUsername(String username) {
        // 用户名经账号域值对象规范化/校验；非法用户名视为不存在（返回 null，不向上抛账号域异常）。
        Username vo;
        try {
            vo = Username.of(username);
        } catch (RuntimeException ex) {
            return null;
        }
        return userRepository.findByUsername(vo)
                .map(User::id)
                .orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    public UserSnapshot findSnapshotById(long userId) {
        return userRepository.findById(userId)
                .map(AccountPasskeyUserDirectory::toSnapshot)
                .orElse(null);
    }

    /**
     * 账号域用户聚合 → 中性快照（仅客户视图字段，零敏感）。
     *
     * @param u 账号域用户聚合
     * @return passkey 域中性快照
     */
    private static UserSnapshot toSnapshot(User u) {
        return new UserSnapshot(
                u.id(),
                u.username().value(),
                u.role().code(),
                u.status().code(),
                u.quota(),
                u.affCode(),
                u.email() == null ? null : u.email().value(),
                u.lastLoginAt());
    }
}
