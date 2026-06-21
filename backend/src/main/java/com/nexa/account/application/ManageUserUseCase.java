package com.nexa.account.application;

import com.nexa.account.domain.exception.InvalidCredentialException;
import com.nexa.account.domain.exception.UserNotFoundException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户状态管理用例（应用服务，F-1010）。
 *
 * <p>编排「管理员对目标用户执行启用/禁用/提升/降级/删除」的事务边界（PRD AC-10）。
 * 本类<b>薄</b>：定位目标用户 → 调用聚合根的管理类充血方法（角色越权护栏在 {@link User} 内守护）
 * → 持久化。护栏、状态/角色变更等业务规则全在 domain（充血、禁贫血，backend-engineer §2.1）。</p>
 *
 * <p>事务：整个动作在单事务内，「读目标 + 改 + 存」原子；越权（{@code RoleHierarchyViolationException}）
 * 或目标不存在（{@link UserNotFoundException}）时事务回滚、无副作用，幂等动作（已禁用再禁用）无害。</p>
 */
@Service
public class ManageUserUseCase {

    private final UserRepository userRepository;

    /**
     * @param userRepository 用户仓储（domain 接口，infra 实现注入）
     */
    public ManageUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 执行用户状态/角色管理动作。
     *
     * @param command 管理命令（接口层翻译后的入参，含操作者角色用于护栏）
     * @throws UserNotFoundException 目标用户不存在（404）
     * @throws com.nexa.account.domain.exception.RoleHierarchyViolationException 角色越权（403）
     * @throws InvalidCredentialException 未知 action（400）
     */
    @Transactional
    public void manage(ManageUserCommand command) {
        // 操作者角色：脏编码（数据库/请求异常）由 Role.fromCode 抛 IllegalArgumentException 兜底。
        Role operatorRole = Role.fromCode(command.operatorRoleCode());

        User target = userRepository.findById(command.targetUserId())
                .orElseThrow(() -> new UserNotFoundException(command.targetUserId()));

        // 动作分发：状态/角色变更与护栏全在聚合根行为内（M3/M7 护栏不在此处重复判断）。
        String action = command.action() == null ? "" : command.action().trim().toLowerCase();
        switch (action) {
            case "enable" -> target.enable(operatorRole);
            case "disable" -> target.disable(operatorRole);
            case "promote" -> target.promote(operatorRole);
            case "demote" -> target.demote(operatorRole);
            case "delete" -> {
                // 删除前同样过角色护栏，再软删除（DeletedAt 由仓储 delete 落地）。
                target.ensureOperableBy(operatorRole);
                userRepository.softDelete(target.id());
                return; // 删除路径不再 save（已在仓储内落软删除时间戳）
            }
            default -> throw new InvalidCredentialException("unknown manage action: " + command.action());
        }
        userRepository.save(target);
    }
}
