package com.nexa.account.application;

import com.nexa.account.application.port.AccountSettings;
import com.nexa.account.domain.exception.UserAlreadyExistsException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.PasswordHasher;
import com.nexa.account.domain.vo.RawPassword;
import com.nexa.account.domain.vo.Role;
import com.nexa.account.domain.vo.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端创建用户用例（应用服务，F-1009）。
 *
 * <p>编排「管理员手动创建用户」的事务边界（API-ENDPOINTS §1.4）。本类薄：构造值对象（触发格式校验）
 * → 用户名查重 → 调用聚合根工厂 {@link User#createByAdmin}（角色越权护栏在 domain 内守护）→ 持久化。
 * 与自助注册 {@link RegisterUserUseCase} 的区别：不走注册开关/验证码，角色可在护栏内指定。</p>
 *
 * <p>事务：查重与保存原子；用户名唯一索引兜底并发竞态（仓储 save 时翻译为
 * {@link UserAlreadyExistsException}）。</p>
 */
@Service
public class CreateUserByAdminUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccountSettings settings;

    /**
     * @param userRepository 用户仓储（domain 接口）
     * @param passwordHasher 密码哈希器（domain 端口）
     * @param settings       账号设置端口（取初始额度）
     */
    public CreateUserByAdminUseCase(UserRepository userRepository,
                                    PasswordHasher passwordHasher,
                                    AccountSettings settings) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.settings = settings;
    }

    /**
     * 执行管理端创建用户。
     *
     * @param command 创建命令（含操作者角色用于护栏）
     * @return 持久化后的新用户聚合（含 id）
     * @throws UserAlreadyExistsException 用户名已存在
     * @throws com.nexa.account.domain.exception.RoleHierarchyViolationException 越权创建（角色 >= 操作者）
     * @throws com.nexa.account.domain.exception.InvalidCredentialException 用户名/密码/邮箱非法
     */
    @Transactional
    public User create(CreateUserByAdminCommand command) {
        Username username = Username.of(command.username());
        RawPassword rawPassword = RawPassword.of(command.rawPassword());
        Email email = (command.email() == null || command.email().isBlank())
                ? null
                : Email.of(command.email());
        Role newRole = Role.fromCode(command.roleCode());
        Role operatorRole = Role.fromCode(command.operatorRoleCode());

        // 查重（并发兜底由 username 唯一索引在 save 时暴露）。
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException(username.value());
        }

        // 护栏在聚合工厂内：newRole >= operatorRole 抛 RoleHierarchyViolationException。
        User user = User.createByAdmin(
                username, rawPassword, email, command.displayName(), newRole,
                passwordHasher, settings.quotaForNewUser(), operatorRole);

        return userRepository.save(user);
    }
}
