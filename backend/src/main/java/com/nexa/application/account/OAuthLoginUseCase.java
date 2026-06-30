package com.nexa.application.account;

import com.nexa.application.account.port.OAuthClient;
import com.nexa.application.account.port.OAuthStateStore;
import com.nexa.application.account.port.OAuthUserInfo;
import com.nexa.application.account.port.AccountSettings;
import com.nexa.application.account.port.DomainEventPublisher;
import com.nexa.application.account.port.TokenIssuer;
import com.nexa.domain.account.event.UserRegistered;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.exception.InvalidOAuthStateException;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.OAuthBindingRepository;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.OAuthProvider;
import com.nexa.domain.account.vo.OAuthState;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import com.nexa.application.account.command.OAuthLoginCommand;
import com.nexa.application.account.result.LoginResult;
import com.nexa.application.account.result.OAuthLoginResult;

/**
 * OAuth 登录/建号用例（应用服务，F-1016~1020）。
 *
 * <p>编排第三方 OAuth 回调的事务边界：
 * <ol>
 *   <li><b>CSRF 校验</b>：用回调带回的 state token 向 {@link OAuthStateStore#consume} 一次性取回暂存 state，
 *       取不到（不存在/过期/已消费）即抛 {@link InvalidOAuthStateException}（防 CSRF/重放）。</li>
 *   <li><b>换 userinfo</b>：据命令里的 provider 经 {@link OAuthClientRegistry} 取对应 {@link OAuthClient}，
 *       用授权码换取并归一化第三方用户信息 {@link OAuthUserInfo}。</li>
 *   <li><b>找绑定分两路</b>：据 (provider, providerUserId) 查 {@link OAuthBindingRepository}：
 *     <ul>
 *       <li><b>已绑定 → 登录</b>：取绑定归属用户，刷新最近登录时间，签发令牌返回（{@code newlyCreated=false}）。</li>
 *       <li><b>未绑定 → 建号 + 绑定</b>：用 {@link User#registerViaOAuth} 建本地账号（随机占位密码、common、启用），
 *           落库拿 id 后建一条 {@link OAuthBinding} 落库，签发令牌返回（{@code newlyCreated=true}）。
 *           建号产生 {@link UserRegistered} 事件发布（与普通注册一致的下游归因）。</li>
 *     </ul>
 *   </li>
 * </ol>
 * 业务规则（建号不变量、绑定不变量）在 domain 充血方法上，本类只编排（backend-engineer §2.1）。
 * 整个方法在单事务内：建号与建绑定原子，复合唯一索引兜底并发竞态。</p>
 *
 * <p>对齐 openapi {@code GET /api/oauth/{provider}}（F-1016）、{@code /api/oauth/discord}（F-1018）、
 * {@code /api/oauth/linuxdo}（F-1020）；返回投影为 {@code UserVO}，token 不进 body（产品铁律，沿用
 * {@link LoginResult} 约定）。邀请归因 {@code state.aff()} 解析留待后续 wave，本切片以 0 处理。</p>
 */
@Service
public class OAuthLoginUseCase {

    /** 建号时候选用户名前缀（provider code + providerUserId 片段，避免与本地注册用户名碰撞语义）。 */
    private static final int USERNAME_MAX = Username.MAX_LENGTH;

    private final OAuthStateStore stateStore;
    private final OAuthClientRegistry clientRegistry;
    private final OAuthBindingRepository oauthBindingRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final AccountSettings settings;
    private final DomainEventPublisher eventPublisher;

    /**
     * @param stateStore             state 暂存端口（CSRF 一次性消费）
     * @param clientRegistry         OAuth 客户端注册表（按 provider 路由实现）
     * @param oauthBindingRepository OAuth 绑定仓储（domain 接口）
     * @param userRepository         用户仓储（domain 接口）
     * @param passwordHasher         密码哈希器（domain 端口，建号时哈希随机占位密码）
     * @param tokenIssuer            访问令牌签发端口
     * @param settings               账号系统设置端口（新用户初始额度）
     * @param eventPublisher         领域事件发布端口（建号发 UserRegistered）
     */
    public OAuthLoginUseCase(OAuthStateStore stateStore,
                             OAuthClientRegistry clientRegistry,
                             OAuthBindingRepository oauthBindingRepository,
                             UserRepository userRepository,
                             PasswordHasher passwordHasher,
                             TokenIssuer tokenIssuer,
                             AccountSettings settings,
                             DomainEventPublisher eventPublisher) {
        this.stateStore = stateStore;
        this.clientRegistry = clientRegistry;
        this.oauthBindingRepository = oauthBindingRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.settings = settings;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行 OAuth 登录/建号。
     *
     * @param command OAuth 登录命令（provider + code + state）
     * @return 登录/建号结果（用户聚合 + 令牌 + 是否新建）
     * @throws InvalidOAuthStateException   state 无效/过期/已消费（CSRF 校验失败）
     * @throws InvalidCredentialException   provider 非法/未配置、或 code 为空
     */
    @Transactional
    public OAuthLoginResult login(OAuthLoginCommand command) {
        // ① CSRF 校验：state 必须能从 store 一次性取回（不存在/过期/已消费 → 拒绝）。
        OAuthState state = stateStore.consume(command.state())
                .orElseThrow(InvalidOAuthStateException::new);

        // provider 标识串 → 枚举（非法/不支持的 provider 在此抛 InvalidCredentialException）。
        OAuthProvider provider = OAuthProvider.fromCode(command.provider());

        if (command.code() == null || command.code().isBlank()) {
            // 授权码缺失：第三方未成功授权或回调被篡改，拒绝。
            throw new InvalidCredentialException("oauth authorization code must not be blank");
        }

        // ② 据 provider 取对应客户端，用授权码换取并归一化第三方用户信息。
        OAuthClient client = clientRegistry.resolve(provider);
        OAuthUserInfo userInfo = client.exchangeCodeForUserInfo(command.code());
        if (userInfo == null || userInfo.providerUserId() == null || userInfo.providerUserId().isBlank()) {
            // 归一化结果缺少绑定锚点：无法可靠找绑定/建号，拒绝。
            throw new InvalidCredentialException("oauth provider returned no user id");
        }

        // ③ 据 (provider, providerUserId) 找绑定，分两路。
        Optional<OAuthBinding> existing =
                oauthBindingRepository.findByProviderAndProviderUserId(provider, userInfo.providerUserId());

        if (existing.isPresent()) {
            // —— 已绑定 → 直接登录 ——
            return loginExisting(existing.get());
        }
        // —— 未绑定 → 建号 + 绑定 ——
        return registerAndBind(provider, userInfo, state);
    }

    /**
     * 已绑定路径：取绑定归属用户登录，刷新登录时间并签发令牌。
     *
     * @param binding 命中的绑定
     * @return 登录结果（newlyCreated=false）
     * @throws InvalidCredentialException 绑定归属用户已不存在（数据不一致，防御式）
     */
    private OAuthLoginResult loginExisting(OAuthBinding binding) {
        User user = userRepository.findById(binding.userId())
                // 绑定存在但用户缺失：数据不一致（用户被软删但绑定残留），拒绝登录。
                .orElseThrow(() -> new InvalidCredentialException("bound user no longer exists"));

        // OAuth 登录不再校验本地密码（身份由第三方背书）；刷新最近登录时间（对齐普通登录 PRD AC-2 §5）。
        user.markLoggedIn(Instant.now().getEpochSecond());
        User saved = userRepository.save(user);

        String token = tokenIssuer.issue(saved);
        return new OAuthLoginResult(saved, token, false);
    }

    /**
     * 未绑定路径：建本地账号 + 建绑定，发注册事件并签发令牌。
     *
     * @param provider provider 枚举
     * @param userInfo 归一化第三方用户信息
     * @param state    暂存的 state（含可选 aff，邀请归因留待后续 wave）
     * @return 建号 + 登录结果（newlyCreated=true）
     */
    private OAuthLoginResult registerAndBind(OAuthProvider provider, OAuthUserInfo userInfo, OAuthState state) {
        // 候选用户名：基于 provider + providerUserId 生成可落库的稳定候选，并查重直至可用（最多几次）。
        Username username = allocateUsername(provider, userInfo.username(), userInfo.providerUserId());

        // 邮箱：provider 可能未返回或返回非法格式，宽松处理——非法/缺省则不落邮箱（不因 OAuth 邮箱脏数据挡住建号）。
        Email email = parseEmailLenient(userInfo.email());

        // 邀请归因：state 暂存了 aff，但本切片邀请解析尚未接入（与 RegisterUserUseCase 一致以 0 处理）。
        // TODO 后续 wave 据 state.aff() 解析邀请人 id 传入。
        long inviterId = 0L;

        // 建号：OAuth 用户无自设密码，聚合内用随机占位哈希落库（NOT NULL 约束），common + 启用。
        User newUser = User.registerViaOAuth(
                username, email, passwordHasher, settings.quotaForNewUser(), inviterId);
        User savedUser = userRepository.save(newUser);

        // 建绑定：绑定锚点 (provider, providerUserId)，归属新建用户。复合唯一索引由实现层兜底冲突翻译。
        OAuthBinding binding = OAuthBinding.create(savedUser.id(), provider, userInfo.providerUserId());
        oauthBindingRepository.save(binding);

        // 发注册事件（带数据库 id），与普通注册一致的下游归因。
        UserRegistered pending = savedUser.pullRegisteredEvent();
        if (pending != null) {
            eventPublisher.publish(new UserRegistered(
                    savedUser.id(), pending.username(), pending.inviterId(), pending.occurredAt()));
        }

        // 首登即登录：签发令牌（不刷新 last_login_at 亦可，这里复用登录语义打点）。
        savedUser.markLoggedIn(Instant.now().getEpochSecond());
        userRepository.save(savedUser);

        String token = tokenIssuer.issue(savedUser);
        return new OAuthLoginResult(savedUser, token, true);
    }

    /**
     * 为 OAuth 建号分配一个可落库且唯一的用户名。
     *
     * <p>优先用 provider 返回的用户名（裁剪到长度上限）；为空或已占用时回退为
     * {@code <provider>_<providerUserId 片段>}，仍冲突则追加随机后缀重试有限次。用户名唯一性
     * 最终由 {@code users.username} 唯一索引兜底（保存时冲突翻译为 UserAlreadyExists）。</p>
     *
     * @param provider       provider 枚举
     * @param candidate      provider 返回的候选用户名（可空）
     * @param providerUserId 第三方账号 id（兜底前缀来源）
     * @return 查重后可用的用户名值对象
     */
    private Username allocateUsername(OAuthProvider provider, String candidate, String providerUserId) {
        // 1) 优先用 provider 返回的用户名（清洗 + 截断）。
        String base = sanitize(candidate);
        if (base.isEmpty()) {
            // 2) 回退：provider code + providerUserId 片段（稳定可读，跨重试确定性前缀）。
            base = sanitize(provider.code() + "_" + providerUserId);
        }
        if (base.isEmpty()) {
            base = "user"; // 极端兜底（providerUserId 全为非法字符）。
        }
        String trimmedBase = truncate(base, USERNAME_MAX);
        Username first = Username.of(trimmedBase);
        if (!userRepository.existsByUsername(first)) {
            return first;
        }
        // 3) 冲突重试：追加短随机后缀（4 位），裁剪保证总长不超限。最多试若干次后交由唯一索引兜底。
        for (int i = 0; i < 5; i++) {
            String suffix = randomSuffix();
            String head = truncate(base, USERNAME_MAX - suffix.length() - 1);
            Username candidateName = Username.of(head + "_" + suffix);
            if (!userRepository.existsByUsername(candidateName)) {
                return candidateName;
            }
        }
        // 兜底：返回最后一次候选，保存时若仍冲突由唯一索引抛 UserAlreadyExists（并发极小概率）。
        String suffix = randomSuffix();
        String head = truncate(base, USERNAME_MAX - suffix.length() - 1);
        return Username.of(head + "_" + suffix);
    }

    /**
     * 清洗候选用户名：trim、去除控制/空白字符，仅保留可读字符。
     *
     * @param raw 原始候选
     * @return 清洗后的串（可能为空）
     */
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        // 仅保留字母/数字/下划线/连字符/点（其余替换为空），避免 provider 返回的奇异字符污染用户名。
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_.\\-]", "");
        return cleaned;
    }

    /**
     * 安全截断到指定长度（短于上限则原样返回）。
     *
     * @param s   待截断串
     * @param max 上限
     * @return 截断后的串
     */
    private static String truncate(String s, int max) {
        if (max <= 0) {
            return s;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 生成 4 位小写字母数字随机后缀（用户名查重重试用）。
     *
     * @return 随机后缀
     */
    private static String randomSuffix() {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * 宽松解析 OAuth 返回的邮箱：空或格式非法时返回 {@code null}（不因脏邮箱挡住建号）。
     *
     * @param rawEmail provider 返回的邮箱（可空 / 可能非法）
     * @return 合法邮箱值对象，或 {@code null}
     */
    private static Email parseEmailLenient(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }
        try {
            return Email.of(rawEmail);
        } catch (InvalidCredentialException e) {
            // OAuth 邮箱不可信/可能为占位（如 GitHub noreply）：格式非法时不落库邮箱，建号照常。
            return null;
        }
    }
}
