package com.nexa.application.account;

import com.nexa.application.account.port.AccountSettings;
import com.nexa.application.account.port.DomainEventPublisher;
import com.nexa.application.account.port.OAuthClient;
import com.nexa.application.account.port.OAuthStateStore;
import com.nexa.application.account.port.OAuthUserInfo;
import com.nexa.application.account.port.TokenIssuer;
import com.nexa.domain.account.event.UserRegistered;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.OAuthBindingRepository;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.OAuthProvider;
import com.nexa.domain.account.vo.OAuthState;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.Role;
import com.nexa.domain.account.vo.UserStatus;
import com.nexa.domain.account.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.nexa.application.account.command.OAuthLoginCommand;
import com.nexa.application.account.result.OAuthLoginResult;

/**
 * {@link OAuthLoginUseCase} 应用层单测（Mockito mock 端口/仓储，不起 Spring/DB）。
 *
 * <p>覆盖 OAuth 回调编排的两条核心路径（F-1016~1020）：
 * <ul>
 *   <li><b>已绑定 → 直接登录</b>：据 (provider, providerUserId) 命中绑定，取归属用户刷新登录时间、
 *       签发令牌返回（{@code newlyCreated=false}），<b>不</b>新建用户/绑定。</li>
 *   <li><b>未绑定 → 建号 + 绑定</b>：未命中绑定时建本地账号（{@code registerViaOAuth}）、落库拿 id、
 *       建一条绑定落库、发 {@link UserRegistered} 事件、签发令牌返回（{@code newlyCreated=true}）。</li>
 * </ul>
 * state CSRF 校验、provider 路由由用例编排，本测以 mock 桩注入；业务不变量本身在 domain 已单测。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthLoginUseCase OAuth 登录/建号编排")
class OAuthLoginUseCaseTest {

    @Mock
    private OAuthStateStore stateStore;
    @Mock
    private OAuthClientRegistry clientRegistry;
    @Mock
    private OAuthClient oauthClient;
    @Mock
    private OAuthBindingRepository oauthBindingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private AccountSettings settings;
    @Mock
    private DomainEventPublisher eventPublisher;

    /** 被测用例（构造器注入显式装配，避免 @InjectMocks 在多 mock 端口下歧义）。 */
    private OAuthLoginUseCase useCase() {
        return new OAuthLoginUseCase(stateStore, clientRegistry, oauthBindingRepository,
                userRepository, passwordHasher, tokenIssuer, settings, eventPublisher);
    }

    /** 构造一个落地态用户聚合（用于已绑定路径返回归属用户）。 */
    private User existingUser(long id) {
        return User.rehydrate(
                id, Username.of("ghuser"), "STORED", null,
                Role.COMMON, UserStatus.ENABLED, 0L, "ABCD", 0L, 0L);
    }

    @Test
    @DisplayName("已绑定：state 通过→换 userinfo→命中绑定→刷新登录时间、签发令牌、不建号")
    void login_existingBinding_loginsWithoutCreatingUser() {
        // state CSRF 校验通过（一次性消费返回暂存 state）。
        OAuthState state = OAuthState.generate(null);
        when(stateStore.consume("state-token")).thenReturn(Optional.of(state));

        // provider 路由到 mock 客户端，授权码换得归一化 userinfo（GitHub 数字 id 作 providerUserId）。
        when(clientRegistry.resolve(OAuthProvider.GITHUB)).thenReturn(oauthClient);
        when(oauthClient.exchangeCodeForUserInfo("auth-code"))
                .thenReturn(new OAuthUserInfo("99001", "ghuser", "ghuser@example.com"));

        // 据 (provider, providerUserId) 命中已存在绑定，归属 user id=7。
        OAuthBinding binding = OAuthBinding.rehydrate(
                1L, 7L, OAuthProvider.GITHUB, "99001", Instant.now());
        when(oauthBindingRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, "99001"))
                .thenReturn(Optional.of(binding));

        User bound = existingUser(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(bound));
        // save 为更新场景，原样返回入参。
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issue(any(User.class))).thenReturn("jwt-existing");

        OAuthLoginCommand command = new OAuthLoginCommand("github", "auth-code", "state-token", null);
        OAuthLoginResult result = useCase().login(command);

        assertNotNull(result);
        assertFalse(result.newlyCreated(), "已绑定路径应为直接登录（newlyCreated=false）");
        assertEquals("jwt-existing", result.token());
        assertEquals(7L, result.user().id());
        assertTrue(result.user().lastLoginAt() > 0L, "已绑定登录应刷新 last_login_at 为正数 epoch 秒");

        // 已绑定路径：不新建绑定、不发注册事件。
        verify(oauthBindingRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
        verify(tokenIssuer).issue(any(User.class));
    }

    @Test
    @DisplayName("未绑定：state 通过→换 userinfo→未命中→建号+建绑定+发事件+签发令牌")
    void login_noBinding_createsUserAndBinding() {
        // state CSRF 校验通过。
        OAuthState state = OAuthState.generate(null);
        when(stateStore.consume("state-token")).thenReturn(Optional.of(state));

        when(clientRegistry.resolve(OAuthProvider.GITHUB)).thenReturn(oauthClient);
        when(oauthClient.exchangeCodeForUserInfo("auth-code"))
                .thenReturn(new OAuthUserInfo("99001", "ghuser", "ghuser@example.com"));

        // 据 (provider, providerUserId) 未命中绑定 → 走建号 + 绑定。
        when(oauthBindingRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, "99001"))
                .thenReturn(Optional.empty());

        // 建号协作者：新用户初始额度、密码哈希（占位随机密码）、用户名查重未占用。
        when(settings.quotaForNewUser()).thenReturn(500L);
        when(passwordHasher.hash(anyString())).thenReturn("HASHED");
        when(userRepository.existsByUsername(any(Username.class))).thenReturn(false);
        // save 回填 id 后返回（模拟数据库生成主键）。
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.id() == null) {
                u.assignId(42L);
            }
            return u;
        });
        // 绑定保存原样返回（id 回填非本测关注点）。
        when(oauthBindingRepository.save(any(OAuthBinding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issue(any(User.class))).thenReturn("jwt-new");
        // 已绑定路径用到的 findById 在本路径不触发；声明 lenient 以防严格 stubbing 警告。
        lenient().when(userRepository.findById(eq(42L))).thenReturn(Optional.empty());

        OAuthLoginCommand command = new OAuthLoginCommand("github", "auth-code", "state-token", null);
        OAuthLoginResult result = useCase().login(command);

        assertNotNull(result);
        assertTrue(result.newlyCreated(), "未绑定路径应新建账号（newlyCreated=true）");
        assertEquals("jwt-new", result.token());
        assertEquals(42L, result.user().id());

        // 建绑定：锚点 (provider, providerUserId) 归属新建用户 id。
        ArgumentCaptor<OAuthBinding> bindingCaptor = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(oauthBindingRepository).save(bindingCaptor.capture());
        assertEquals(OAuthProvider.GITHUB, bindingCaptor.getValue().provider());
        assertEquals("99001", bindingCaptor.getValue().providerUserId());
        assertEquals(42L, bindingCaptor.getValue().userId());

        // 发带 id 的注册事件（与普通注册一致的下游归因）。
        ArgumentCaptor<UserRegistered> eventCaptor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals(42L, eventCaptor.getValue().userId());

        verify(tokenIssuer).issue(any(User.class));
    }
}
