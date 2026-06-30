package com.nexa.application.telegram;

import com.nexa.application.account.port.AccountSettings;
import com.nexa.application.account.port.DomainEventPublisher;
import com.nexa.application.account.port.TokenIssuer;
import com.nexa.domain.account.event.UserRegistered;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.Username;
import com.nexa.application.telegram.port.TelegramSettings;
import com.nexa.domain.telegram.exception.InvalidTelegramAuthException;
import com.nexa.domain.telegram.exception.TelegramUserNotFoundException;
import com.nexa.domain.telegram.model.TelegramBinding;
import com.nexa.domain.telegram.repository.TelegramBindingRepository;
import com.nexa.domain.telegram.service.TelegramHmacVerifier;
import com.nexa.domain.telegram.vo.TelegramAuthData;
import com.nexa.domain.telegram.vo.TelegramId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import com.nexa.application.telegram.command.TelegramLoginCommand;
import com.nexa.application.telegram.result.TelegramLoginResult;

/**
 * Telegram 登录/建号用例（应用服务，F-1051/F-1053）。
 *
 * <p>编排 Telegram Login Widget 回调的事务边界（与 {@code com.nexa.account.OAuthLoginUseCase} 同构，
 * 但走 HMAC 而非授权码）：
 * <ol>
 *   <li><b>开关闸门</b>：Telegram 登录未启用直接拒绝。</li>
 *   <li><b>HMAC 防伪</b>：用 {@link TelegramHmacVerifier#requireAuthentic} 校验回传 hash 与 Bot Token
 *       重算结果一致（F-1053）；不一致即拒绝（参数被篡改/伪造）。</li>
 *   <li><b>时效校验</b>：auth_date 超出有效窗口视为过期拒绝（防重放）。</li>
 *   <li><b>找绑定分两路</b>：据 telegram_id 查绑定仓储：
 *     <ul>
 *       <li>已绑定 → 取归属用户登录，刷新 last_login，签发令牌（newlyCreated=false）。</li>
 *       <li>未绑定 → 建本地账号（随机占位密码、common、启用）+ 建 Telegram 绑定，发注册事件，
 *           签发令牌（newlyCreated=true）。</li>
 *     </ul>
 *   </li>
 * </ol>
 * 业务规则（HMAC 校验、建号不变量、绑定不变量）在 domain 充血方法/领域服务上，本类只编排
 * （backend-engineer §2.1）。整个方法单事务：建号与建绑定原子，唯一索引兜底并发竞态。</p>
 *
 * <p>对齐 openapi {@code GET /api/oauth/telegram/login}（F-1051，security: []）；返回投影为
 * {@code UserVO}，token 不进 body（产品铁律）。邀请归因留待后续 wave（以 0 处理）。</p>
 */
@Service
public class TelegramLoginUseCase {

    /** OAuth/外部登录建号时随机占位密码字节数（与 account 聚合一致）。 */
    private static final int RANDOM_PASSWORD_BYTES = 24;

    private final TelegramBindingRepository telegramBindingRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final AccountSettings accountSettings;
    private final TelegramSettings telegramSettings;
    private final DomainEventPublisher eventPublisher;

    /**
     * @param telegramBindingRepository Telegram 绑定仓储（domain 接口）
     * @param userRepository            用户仓储（复用 account 域）
     * @param passwordHasher            密码哈希器（建号时哈希随机占位密码）
     * @param tokenIssuer               访问令牌签发端口
     * @param accountSettings           账号设置（新用户初始额度）
     * @param telegramSettings          Telegram 设置（开关 + Bot Token + 时效窗口）
     * @param eventPublisher            领域事件发布端口（建号发 UserRegistered）
     */
    public TelegramLoginUseCase(TelegramBindingRepository telegramBindingRepository,
                                UserRepository userRepository,
                                PasswordHasher passwordHasher,
                                TokenIssuer tokenIssuer,
                                AccountSettings accountSettings,
                                TelegramSettings telegramSettings,
                                DomainEventPublisher eventPublisher) {
        this.telegramBindingRepository = telegramBindingRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.accountSettings = accountSettings;
        this.telegramSettings = telegramSettings;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行 Telegram 登录/建号（F-1051）。
     *
     * @param command 登录命令（Login Widget 全部回传参数）
     * @return 登录/建号结果（用户聚合 + 令牌 + 是否新建）
     * @throws InvalidTelegramAuthException Telegram 登录未启用 / HMAC 校验失败 / 授权过期 / 参数非法
     * @throws TelegramUserNotFoundException 已绑定但归属用户已不存在（数据不一致，防御式）
     */
    @Transactional
    public TelegramLoginResult login(TelegramLoginCommand command) {
        TelegramAuthData authData = verifyAndParse(command.params());
        TelegramId telegramId = authData.telegramId();

        Optional<TelegramBinding> existing = telegramBindingRepository.findByTelegramId(telegramId);
        if (existing.isPresent()) {
            return loginExisting(existing.get());
        }
        return registerAndBind(telegramId);
    }

    /**
     * 开关闸门 + HMAC 防伪 + 时效校验 + 参数解析（F-1051/F-1053 的安全前置）。
     *
     * <p>抽出复用：登录与绑定两个用例都必须先过这同一道校验。任何一步失败都抛
     * {@link InvalidTelegramAuthException}，由接口层统一映射 400。</p>
     *
     * @param params Login Widget 全部回传参数
     * @return 校验通过的授权数据
     * @throws InvalidTelegramAuthException 未启用 / 校验失败 / 过期 / 参数非法
     */
    TelegramAuthData verifyAndParse(java.util.Map<String, String> params) {
        if (!telegramSettings.isTelegramLoginEnabled()) {
            // 开关关闭：不暴露具体配置，统一拒绝。
            throw new InvalidTelegramAuthException("telegram login is not enabled");
        }
        // 解析 + 字段级校验（id/hash/auth_date 必备且格式合法）。
        TelegramAuthData authData = TelegramAuthData.fromParams(params);

        // F-1053 防伪铁律：HMAC 不一致即拒绝（参数被篡改/伪造）。
        TelegramHmacVerifier.requireAuthentic(authData, telegramSettings.botToken());

        // 防重放：auth_date 超出有效窗口视为过期（<=0 表示不校验时效）。
        long window = telegramSettings.authValiditySeconds();
        if (window > 0) {
            long now = Instant.now().getEpochSecond();
            long age = now - authData.authDate();
            // age 为负（auth_date 在未来）也视为异常：时钟漂移容忍内可放行，超窗即拒。
            if (age > window) {
                throw new InvalidTelegramAuthException("telegram login has expired, please retry");
            }
        }
        return authData;
    }

    /**
     * 已绑定路径：取归属用户登录，刷新登录时间并签发令牌。
     *
     * @param binding 命中的绑定
     * @return 登录结果（newlyCreated=false）
     * @throws TelegramUserNotFoundException 绑定归属用户已不存在（数据不一致）
     */
    private TelegramLoginResult loginExisting(TelegramBinding binding) {
        User user = userRepository.findById(binding.userId())
                .orElseThrow(() -> new TelegramUserNotFoundException(
                        "telegram-bound user no longer exists"));
        // Telegram 登录由 HMAC 背书身份，不校验本地密码；刷新最近登录时间（对齐 PRD AC-2 §5）。
        user.markLoggedIn(Instant.now().getEpochSecond());
        User saved = userRepository.save(user);
        return new TelegramLoginResult(saved, tokenIssuer.issue(saved), false);
    }

    /**
     * 未绑定路径：建本地账号 + 建 Telegram 绑定，发注册事件并签发令牌。
     *
     * @param telegramId Telegram 账号 id
     * @return 建号 + 登录结果（newlyCreated=true）
     */
    private TelegramLoginResult registerAndBind(TelegramId telegramId) {
        // 候选用户名：tg_<telegramId 片段>，查重后落库（最终由 users.username 唯一索引兜底）。
        Username username = allocateUsername(telegramId);

        // Telegram 登录无第三方邮箱，email 置 null；邀请归因本切片以 0 处理（与 OAuth 用例一致）。
        User newUser = User.registerViaOAuth(
                username, null, passwordHasher, accountSettings.quotaForNewUser(), 0L);
        User savedUser = userRepository.save(newUser);

        // 建绑定：telegram_id 唯一，归属新建用户。唯一索引由实现层兜底冲突翻译（并发竞态）。
        TelegramBinding binding = TelegramBinding.create(savedUser.id(), telegramId);
        telegramBindingRepository.save(binding);

        // 发注册事件（带数据库 id），与普通注册一致的下游归因。
        UserRegistered pending = savedUser.pullRegisteredEvent();
        if (pending != null) {
            eventPublisher.publish(new UserRegistered(
                    savedUser.id(), pending.username(), pending.inviterId(), pending.occurredAt()));
        }

        savedUser.markLoggedIn(Instant.now().getEpochSecond());
        userRepository.save(savedUser);
        return new TelegramLoginResult(savedUser, tokenIssuer.issue(savedUser), true);
    }

    /**
     * 为 Telegram 建号分配可落库且唯一的用户名（{@code tg_<telegramId>}，冲突追加随机后缀）。
     *
     * @param telegramId Telegram 账号 id
     * @return 查重后可用的用户名值对象
     */
    private Username allocateUsername(TelegramId telegramId) {
        String base = truncate("tg_" + telegramId.value(), Username.MAX_LENGTH);
        Username first = Username.of(base);
        if (!userRepository.existsByUsername(first)) {
            return first;
        }
        for (int i = 0; i < 5; i++) {
            String suffix = randomSuffix();
            String head = truncate("tg_" + telegramId.value(), Username.MAX_LENGTH - suffix.length() - 1);
            Username candidate = Username.of(head + "_" + suffix);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        // 兜底：保存时若仍冲突由唯一索引抛冲突（并发极小概率）。
        String suffix = randomSuffix();
        String head = truncate("tg_" + telegramId.value(), Username.MAX_LENGTH - suffix.length() - 1);
        return Username.of(head + "_" + suffix);
    }

    /**
     * 安全截断到指定长度。
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
}
