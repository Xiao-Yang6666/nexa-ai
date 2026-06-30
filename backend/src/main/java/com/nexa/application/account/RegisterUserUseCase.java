package com.nexa.application.account;

import com.nexa.application.account.port.AccountSettings;
import com.nexa.application.account.port.DomainEventPublisher;
import com.nexa.application.account.port.VerificationCodeService;
import com.nexa.domain.account.event.UserRegistered;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.exception.RegisterDisabledException;
import com.nexa.domain.account.exception.UserAlreadyExistsException;
import com.nexa.domain.account.exception.VerificationCodeException;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.RawPassword;
import com.nexa.domain.account.vo.Username;
import com.nexa.domain.account.vo.VerificationCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nexa.application.account.command.RegisterUserCommand;

/**
 * 注册用户用例（应用服务）。
 *
 * <p>编排注册流程的事务边界（PRD prd-account.md AC-1）：校验系统开关 → 构造领域值对象 →
 * 用户名查重 → 调用聚合根 {@link User#register} 完成不变量与初始状态 → 持久化 → 发布注册事件。
 * 本类<b>薄</b>，业务规则在 domain（充血），这里只做编排与事务（backend-engineer §2.1）。</p>
 *
 * <p>事务：整个方法在单事务内，查重与保存原子；用户名唯一索引兜底并发下的竞态查重漏判。</p>
 */
@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccountSettings settings;
    private final DomainEventPublisher eventPublisher;
    private final VerificationCodeService verificationCodeService;

    /**
     * @param userRepository          用户仓储（domain 接口，infra 实现注入）
     * @param passwordHasher          密码哈希器（domain 端口，infra 实现注入）
     * @param settings                账号系统设置端口（注册开关 / 验证码开关 / 初始额度）
     * @param eventPublisher          领域事件发布端口
     * @param verificationCodeService 邮箱验证码服务端口（F-1005 校验）
     */
    public RegisterUserUseCase(UserRepository userRepository,
                               PasswordHasher passwordHasher,
                               AccountSettings settings,
                               DomainEventPublisher eventPublisher,
                               VerificationCodeService verificationCodeService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.settings = settings;
        this.eventPublisher = eventPublisher;
        this.verificationCodeService = verificationCodeService;
    }

    /**
     * 执行注册。
     *
     * @param command 注册命令（接口层翻译后的入参）
     * @return 持久化后的新用户聚合（含数据库生成 id）
     * @throws RegisterDisabledException    系统开关关闭注册（PRD AC-1 R1-否）
     * @throws UserAlreadyExistsException   用户名已存在（PRD AC-1 R9-是，MsgUserExists）
     * @throws VerificationCodeException     EmailVerificationEnabled 时验证码缺失/不匹配/过期（F-1005）
     * @throws com.nexa.domain.account.exception.InvalidCredentialException 用户名/密码/邮箱非法
     */
    @Transactional
    public User register(RegisterUserCommand command) {
        // R1：注册总开关闸门——关闭则直接拒绝，不进入任何后续校验/落库。
        if (!settings.isRegisterEnabled()) {
            throw new RegisterDisabledException();
        }

        // 构造领域值对象（在此处即触发各自的格式/长度校验，非法输入挡在领域边界外）。
        Username username = Username.of(command.username());
        RawPassword rawPassword = RawPassword.of(command.rawPassword());
        // email 注册时可选：仅当调用方提供非空邮箱时才构造值对象。
        Email email = (command.email() == null || command.email().isBlank())
                ? null
                : Email.of(command.email());

        // R3~R7：邮箱验证码校验（F-1005）。仅当系统开关 EmailVerificationEnabled=true 时启用：
        // 此时邮箱必填，且必须携带与该邮箱匹配且未过期的验证码，否则拒绝创建、不落库（PRD AC-1）。
        if (settings.isEmailVerificationEnabled()) {
            if (email == null) {
                // 开启验证码时邮箱为必填（API-ENDPOINTS §1.1：EmailVerificationEnabled=true 时 email 必填）。
                throw new InvalidCredentialException("email is required when email verification is enabled");
            }
            VerificationCode code = VerificationCode.of(command.verificationCode());
            if (!verificationCodeService.verifyAndConsume(email, code)) {
                // R7-否：验证码不匹配或已过期 → 验证码错误/过期态，不落库。
                throw new VerificationCodeException();
            }
        }

        // R9：用户名查重。并发竞态下两请求可能都查不到，最终由 users.username 唯一索引兜底
        // （保存时抛 DataIntegrityViolation，由基础设施层翻译为 UserAlreadyExistsException）。
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException(username.value());
        }

        // R10~R13：邀请归因本切片暂以 0 处理（aff_code 解析在后续 wave 接入）；
        // 这里把 inviterId 固定为 0，对齐 PRD AC-1 R12「无/无效 aff_code → InviterId=0」。
        long inviterId = 0L;

        User user = User.register(
                username, rawPassword, email, passwordHasher,
                settings.quotaForNewUser(), inviterId);

        User saved = userRepository.save(user);
        // 保存后聚合已持有数据库 id；补一条带 id 的注册事件并发布（事务内）。
        UserRegistered pending = saved.pullRegisteredEvent();
        if (pending != null) {
            eventPublisher.publish(new UserRegistered(
                    saved.id(), pending.username(), pending.inviterId(), pending.occurredAt()));
        }
        return saved;
    }
}
