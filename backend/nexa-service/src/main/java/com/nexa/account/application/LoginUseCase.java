package com.nexa.account.application;

import com.nexa.account.application.port.TokenIssuer;
import com.nexa.account.domain.exception.InvalidCredentialException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.PasswordHasher;
import com.nexa.account.domain.vo.RawPassword;
import com.nexa.account.domain.vo.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 登录用例（应用服务）。
 *
 * <p>编排登录流程的事务边界（PRD prd-account.md AC-2）：定位账号 → 聚合根密码校验 + 状态闸门
 * （{@link User#authenticate}）→ 刷新最近登录时间 → 签发访问令牌。业务规则（密码比对、封禁闸门）
 * 在 domain 充血方法上，本类只编排（backend-engineer §2.1）。</p>
 *
 * <p>账号定位支持「用户名或邮箱」二选一（方案 A，prd-account.md AC-2 / S10 联调修复）：
 * 登录标识字段（{@code command.username()}）先按用户名精确匹配（{@link UserRepository#findByUsername}），
 * 查不到再按邮箱兜底匹配（{@link UserRepository#findByEmail}）。这样无论前端把「用户填的邮箱」
 * 还是「派生的短用户名」放进该字段，都能定位到同一账号——根治「注册填邮箱→用邮箱登录失败」。</p>
 *
 * <p>安全：账号不存在（用户名 + 邮箱两路都查不到）与密码错误统一抛 {@link InvalidCredentialException}，
 * 不区分提示，防账号枚举。值对象构造异常（如用户名超长、邮箱格式非法）在本类被吞为「该路查无此人」，
 * 安全降级为同一笼统错误，绝不向外暴露「字段格式非法」与「账号不存在」的差异。</p>
 */
@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    /**
     * @param userRepository 用户仓储（domain 接口）
     * @param passwordHasher 密码哈希器（domain 端口）
     * @param tokenIssuer    访问令牌签发端口
     */
    public LoginUseCase(UserRepository userRepository,
                        PasswordHasher passwordHasher,
                        TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * 执行登录。
     *
     * @param command 登录命令
     * @return 登录结果（已认证用户 + 已签发令牌）
     * @throws InvalidCredentialException 用户名非法、账号不存在或密码错误（统一不区分）
     * @throws com.nexa.account.domain.exception.UserDisabledException 账号被封禁
     */
    @Transactional
    public LoginResult login(LoginCommand command) {
        // 注意：不在此处用 Username.of(...) 预构造——登录标识可能是邮箱（>20 字符会被
        // Username 校验拒绝），需保留原始输入分别尝试用户名/邮箱两路定位。
        String identifier = command.username();
        RawPassword rawPassword = RawPassword.of(command.rawPassword());

        // 方案 A：先按用户名查，查不到再按邮箱兜底。两路都查不到与密码错误抛同一异常：
        // 避免攻击者通过响应差异枚举已注册用户名/邮箱。
        User user = locateByUsername(identifier)
                .or(() -> locateByEmail(identifier))
                .orElseThrow(() -> new InvalidCredentialException("invalid username or password"));

        // 充血聚合：密码哈希比对（L2）+ 启用状态闸门（L3）都在聚合根内完成。
        user.authenticate(rawPassword, passwordHasher);

        // PRD AC-2 §5：成功登录刷新 last_login_at（epoch 秒）。
        user.markLoggedIn(Instant.now().getEpochSecond());
        userRepository.save(user);

        String token = tokenIssuer.issue(user);
        return new LoginResult(user, token);
    }

    /**
     * 按用户名定位账号。
     *
     * <p>标识若不是合法用户名（如邮箱超过 {@link Username#MAX_LENGTH}、含非法字符），
     * {@link Username#of} 会抛 {@link InvalidCredentialException}——此处安全降级为「该路查无此人」
     * （空 Optional），交由邮箱路兜底，绝不让格式校验差异泄露给调用方（防枚举）。</p>
     *
     * @param identifier 登录标识原始输入
     * @return 命中用户，或空（标识非法/无此用户名）
     */
    private Optional<User> locateByUsername(String identifier) {
        try {
            return userRepository.findByUsername(Username.of(identifier));
        } catch (InvalidCredentialException e) {
            return Optional.empty();
        }
    }

    /**
     * 按邮箱定位账号（用户名查无后的兜底）。
     *
     * <p>标识若不是合法邮箱（格式非法、超长、空），{@link Email#of} 会抛
     * {@link InvalidCredentialException}——此处安全降级为「该路查无此人」（空 Optional），
     * 不抛异常中断登录，最终与密码错误收敛到同一笼统提示（防枚举）。</p>
     *
     * @param identifier 登录标识原始输入
     * @return 命中用户，或空（标识非邮箱/无此邮箱）
     */
    private Optional<User> locateByEmail(String identifier) {
        try {
            return userRepository.findByEmail(Email.of(identifier));
        } catch (InvalidCredentialException e) {
            return Optional.empty();
        }
    }
}
