package com.nexa.application.account;

import com.nexa.application.account.port.AccountSettings;
import com.nexa.application.account.port.DomainEventPublisher;
import com.nexa.application.account.port.OAuthClient;
import com.nexa.application.account.port.OAuthUserInfo;
import com.nexa.application.account.port.TokenIssuer;
import com.nexa.domain.account.event.UserRegistered;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.OAuthBindingRepository;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.OAuthProvider;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import com.nexa.application.account.command.WeChatLoginCommand;
import com.nexa.application.account.result.OAuthLoginResult;

/**
 * WeChat 扫码授权登录/绑定用例（应用服务，F-1022）。
 *
 * <p>对齐 openapi {@code POST /api/oauth/wechat/bind}（requestBody 仅 {@code code}）+ PRD AC-5 §3
 * （W5 携授权码 → W6 判已登录 → 绑定 / 登录）。与标准 {@link OAuthLoginUseCase} 的差异：微信 bind 端点
 * <b>不</b>带 state（其 CSRF 由发起态 {@code GET /oauth/wechat} 的 state + 微信扫码态承载，bind 契约只传 code），
 * 故本用例不消费 StateStore；其余「换 userinfo → 找绑定 → 登录或建号+绑定」与标准流程同构。</p>
 *
 * <p>绑定语义（AC-5 §3 W6）：{@code bindUserId} 非空 = 已登录用户把微信绑到本账号（绑定分支，
 * 第三方账号已被他人绑则冲突）；为空 = 未登录走登录/注册分支（已绑定则登录该账号，未绑定则建号 + 绑定）。
 * 业务不变量在 domain 充血方法上，本类只编排（backend-engineer §2.1）。整个方法单事务，复合唯一索引兜底竞态。</p>
 */
@Service
public class WeChatLoginUseCase {

    private final OAuthClientRegistry clientRegistry;
    private final OAuthBindingRepository oauthBindingRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final AccountSettings settings;
    private final DomainEventPublisher eventPublisher;

    /**
     * @param clientRegistry         OAuth 客户端注册表（按 provider 路由，含 WeChatOAuthClient）
     * @param oauthBindingRepository OAuth 绑定仓储（domain 接口）
     * @param userRepository         用户仓储（domain 接口）
     * @param passwordHasher         密码哈希器（建号时哈希随机占位密码）
     * @param tokenIssuer            访问令牌签发端口
     * @param settings               账号系统设置端口（新用户初始额度）
     * @param eventPublisher         领域事件发布端口（建号发 UserRegistered）
     */
    public WeChatLoginUseCase(OAuthClientRegistry clientRegistry,
                              OAuthBindingRepository oauthBindingRepository,
                              UserRepository userRepository,
                              PasswordHasher passwordHasher,
                              TokenIssuer tokenIssuer,
                              AccountSettings settings,
                              DomainEventPublisher eventPublisher) {
        this.clientRegistry = clientRegistry;
        this.oauthBindingRepository = oauthBindingRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.settings = settings;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行微信登录/绑定。
     *
     * @param command 微信登录/绑定命令（code + 可选 bindUserId）
     * @return 登录/绑定结果（用户聚合 + 令牌 + 是否新建）
     * @throws InvalidCredentialException code 为空 / 微信未配置 / 换取失败 / 绑定归属冲突由 domain 抛出
     */
    @Transactional
    public OAuthLoginResult login(WeChatLoginCommand command) {
        if (command.code() == null || command.code().isBlank()) {
            throw new InvalidCredentialException("wechat authorization code must not be blank");
        }

        // ① 取微信客户端，用授权码换取并归一化用户信息（openid/unionid 为 providerUserId）。
        OAuthClient client = clientRegistry.resolve(OAuthProvider.WECHAT);
        OAuthUserInfo userInfo = client.exchangeCodeForUserInfo(command.code());
        if (userInfo == null || userInfo.providerUserId() == null || userInfo.providerUserId().isBlank()) {
            throw new InvalidCredentialException("wechat provider returned no user id");
        }

        // ② 找绑定。
        Optional<OAuthBinding> existing = oauthBindingRepository
                .findByProviderAndProviderUserId(OAuthProvider.WECHAT, userInfo.providerUserId());

        if (command.bindUserId() != null) {
            // —— 绑定分支（已登录用户绑微信，AC-5 §3 W7）——
            return bindToUser(command.bindUserId(), userInfo, existing);
        }

        if (existing.isPresent()) {
            // —— 未登录 + 微信已绑账号 → 登录该账号（W8-是 / W9）——
            return loginExisting(existing.get());
        }
        // —— 未登录 + 微信未绑账号 → 建号 + 绑定（沿用 OAuth 建号语义）——
        return registerAndBind(userInfo);
    }

    /**
     * 绑定分支：把微信账号绑到指定（已登录）用户。
     *
     * @param userId   当前登录用户 id
     * @param userInfo 微信归一化信息
     * @param existing 据 (wechat, providerUserId) 查到的既有绑定（可能为空）
     * @return 绑定结果（newlyCreated=false，绑定不新建用户）
     */
    private OAuthLoginResult bindToUser(long userId, OAuthUserInfo userInfo, Optional<OAuthBinding> existing) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialException("binding user does not exist"));

        if (existing.isPresent()) {
            // 已存在绑定：若归属本人则幂等通过；归属他人由 ensureOwnedBy 抛冲突（每 provider 一账号唯一）。
            existing.get().ensureOwnedBy(userId);
        } else {
            // 新绑定：建一条 (wechat, providerUserId) → 当前用户。复合唯一索引兜底并发竞态。
            OAuthBinding binding = OAuthBinding.create(userId, OAuthProvider.WECHAT, userInfo.providerUserId());
            oauthBindingRepository.save(binding);
        }
        String token = tokenIssuer.issue(user);
        return new OAuthLoginResult(user, token, false);
    }

    /**
     * 已绑定路径：取归属用户登录，刷新登录时间并签发令牌。
     *
     * @param binding 命中的绑定
     * @return 登录结果（newlyCreated=false）
     */
    private OAuthLoginResult loginExisting(OAuthBinding binding) {
        User user = userRepository.findById(binding.userId())
                .orElseThrow(() -> new InvalidCredentialException("bound user no longer exists"));
        user.markLoggedIn(Instant.now().getEpochSecond());
        User saved = userRepository.save(user);
        String token = tokenIssuer.issue(saved);
        return new OAuthLoginResult(saved, token, false);
    }

    /**
     * 未绑定路径：建本地账号 + 建微信绑定，发注册事件并签发令牌。
     *
     * @param userInfo 微信归一化信息
     * @return 建号 + 登录结果（newlyCreated=true）
     */
    private OAuthLoginResult registerAndBind(OAuthUserInfo userInfo) {
        Username username = allocateUsername(userInfo.username(), userInfo.providerUserId());
        Email email = parseEmailLenient(userInfo.email());

        User newUser = User.registerViaOAuth(username, email, passwordHasher, settings.quotaForNewUser(), 0L);
        User savedUser = userRepository.save(newUser);

        OAuthBinding binding = OAuthBinding.create(savedUser.id(), OAuthProvider.WECHAT, userInfo.providerUserId());
        oauthBindingRepository.save(binding);

        UserRegistered pending = savedUser.pullRegisteredEvent();
        if (pending != null) {
            eventPublisher.publish(new UserRegistered(
                    savedUser.id(), pending.username(), pending.inviterId(), pending.occurredAt()));
        }

        savedUser.markLoggedIn(Instant.now().getEpochSecond());
        userRepository.save(savedUser);

        String token = tokenIssuer.issue(savedUser);
        return new OAuthLoginResult(savedUser, token, true);
    }

    /**
     * 为微信建号分配可落库且唯一的用户名（清洗昵称 → 回退 wechat_<片段> → 随机后缀重试）。
     *
     * @param candidate      微信昵称（可空/含奇异字符）
     * @param providerUserId openid/unionid（兜底前缀）
     * @return 查重后可用的用户名值对象
     */
    private Username allocateUsername(String candidate, String providerUserId) {
        String base = sanitize(candidate);
        if (base.isEmpty()) {
            base = sanitize(OAuthProvider.WECHAT.code() + "_" + providerUserId);
        }
        if (base.isEmpty()) {
            base = "wxuser";
        }
        String trimmedBase = truncate(base, Username.MAX_LENGTH);
        Username first = Username.of(trimmedBase);
        if (!userRepository.existsByUsername(first)) {
            return first;
        }
        for (int i = 0; i < 5; i++) {
            String suffix = randomSuffix();
            String head = truncate(base, Username.MAX_LENGTH - suffix.length() - 1);
            Username candidateName = Username.of(head + "_" + suffix);
            if (!userRepository.existsByUsername(candidateName)) {
                return candidateName;
            }
        }
        String suffix = randomSuffix();
        String head = truncate(base, Username.MAX_LENGTH - suffix.length() - 1);
        return Username.of(head + "_" + suffix);
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("[^A-Za-z0-9_.\\-]", "");
    }

    private static String truncate(String s, int max) {
        if (max <= 0) {
            return s;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String randomSuffix() {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static Email parseEmailLenient(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }
        try {
            return Email.of(rawEmail);
        } catch (InvalidCredentialException e) {
            return null;
        }
    }
}
