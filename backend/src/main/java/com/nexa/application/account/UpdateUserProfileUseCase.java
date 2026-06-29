package com.nexa.application.account;

import com.nexa.domain.account.exception.UserNotFoundException;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.Role;
import com.nexa.domain.account.vo.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端更新用户资料用例（应用服务，F-1011/1013/1014）。
 *
 * <p>编排「管理员一次性覆盖式更新目标用户资料」的事务边界（API-ENDPOINTS §1.4 {@code PUT /api/user/}）。
 * 本类<b>薄</b>：定位目标用户 → 把非 null 入参翻译为值对象 → 调用聚合根充血方法
 * {@link User#updateProfileByAdmin}（角色越权护栏、部分更新、各字段规范化/长度校验全在 domain 内守护）
 * → 持久化。资料更新规则全在领域层（充血、禁贫血，backend-engineer §2.1/§2.2）。</p>
 *
 * <p>事务：「读目标 + 改 + 存」原子；越权（{@code RoleHierarchyViolationException}）、目标不存在
 * （{@link UserNotFoundException}）、字段非法（{@code InvalidCredentialException}）时回滚、无副作用。</p>
 */
@Service
public class UpdateUserProfileUseCase {

    private final UserRepository userRepository;

    /**
     * @param userRepository 用户仓储（domain 接口，infra 实现注入）
     */
    public UpdateUserProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 执行管理端更新用户资料。
     *
     * @param command 更新命令（部分更新语义：null 项不改；含操作者角色用于护栏）
     * @throws UserNotFoundException 目标用户不存在（404）
     * @throws com.nexa.domain.account.exception.RoleHierarchyViolationException 角色越权（403）
     * @throws com.nexa.domain.account.exception.InvalidCredentialException 邮箱格式非法 / 字段超长 / 额度为负（400）
     */
    @Transactional
    public void update(UpdateUserProfileCommand command) {
        Role operatorRole = Role.fromCode(command.operatorRoleCode());

        User target = userRepository.findById(command.targetUserId())
                .orElseThrow(() -> new UserNotFoundException(command.targetUserId()));

        // 仅在显式提供邮箱时构造 Email 值对象（触发格式校验）；null=不改，故不构造。
        Email email = command.email() == null ? null : Email.of(command.email());
        // 状态编码 → 值对象：DB-SCHEMA §1「≠1 视为禁用」由 UserStatus.fromCode 兜底，不抛异常。
        UserStatus status = command.status() == null ? null : UserStatus.fromCode(command.status());

        // 部分更新 + 护栏 + 各字段规范化/长度校验全在聚合根内（应用层不重复判断业务规则）。
        target.updateProfileByAdmin(
                command.displayName(),
                email,
                command.group(),
                command.quota(),
                command.remark(),
                status,
                command.discountRatio(),
                operatorRole);

        userRepository.save(target);
    }
}
