package com.nexa.application.account;

import com.nexa.application.account.port.TokenIssuer;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.exception.UserDisabledException;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.PasswordHasher;
import com.nexa.domain.account.vo.Role;
import com.nexa.domain.account.vo.UserStatus;
import com.nexa.domain.account.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginUseCase} 应用层单测（Mockito mock 仓储/端口，不起 Spring/DB）。
 *
 * <p>覆盖：账号不存在与密码错误统一抛 {@link InvalidCredentialException}（防枚举）、
 * 账号被禁用抛 {@link UserDisabledException}、成功登录刷新登录时间并签发令牌。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUseCase 登录编排")
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenIssuer tokenIssuer;

    @InjectMocks
    private LoginUseCase useCase;

    /**
     * 构造一个落地态用户聚合（指定状态），密码哈希固定为 "STORED"。
     *
     * @param status 账号状态
     * @return 重建的用户聚合
     */
    private User userWithStatus(UserStatus status) {
        return User.rehydrate(
                1L, Username.of("alice"), "STORED", null,
                Role.COMMON, status, 0L, "ABCD", 0L, 0L);
    }

    @Test
    @DisplayName("账号不存在：抛 InvalidCredentialException，不签发令牌")
    void login_userNotFound_throwsInvalidCredential() {
        when(userRepository.findByUsername(Username.of("alice"))).thenReturn(Optional.empty());

        LoginCommand command = new LoginCommand("alice", "password123");
        assertThrows(InvalidCredentialException.class, () -> useCase.login(command));

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("密码错误：抛 InvalidCredentialException，不签发令牌")
    void login_wrongPassword_throwsInvalidCredential() {
        User user = userWithStatus(UserStatus.ENABLED);
        when(userRepository.findByUsername(Username.of("alice"))).thenReturn(Optional.of(user));
        when(passwordHasher.matches(eq("password123"), eq("STORED"))).thenReturn(false);

        LoginCommand command = new LoginCommand("alice", "password123");
        assertThrows(InvalidCredentialException.class, () -> useCase.login(command));

        verify(tokenIssuer, never()).issue(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("账号被禁用：密码正确仍抛 UserDisabledException，不签发令牌")
    void login_disabledAccount_throwsUserDisabled() {
        User user = userWithStatus(UserStatus.DISABLED);
        when(userRepository.findByUsername(Username.of("alice"))).thenReturn(Optional.of(user));
        when(passwordHasher.matches(eq("password123"), eq("STORED"))).thenReturn(true);

        LoginCommand command = new LoginCommand("alice", "password123");
        assertThrows(UserDisabledException.class, () -> useCase.login(command));

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("登录成功：刷新 last_login_at、保存、签发并返回令牌")
    void login_success_marksLoginAndIssuesToken() {
        User user = userWithStatus(UserStatus.ENABLED);
        when(userRepository.findByUsername(Username.of("alice"))).thenReturn(Optional.of(user));
        when(passwordHasher.matches(eq("password123"), eq("STORED"))).thenReturn(true);
        when(tokenIssuer.issue(any(User.class))).thenReturn("jwt-token-xyz");
        // save 是更新场景，原样返回入参。
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginCommand command = new LoginCommand("alice", "password123");
        LoginResult result = useCase.login(command);

        assertNotNull(result);
        assertEquals("jwt-token-xyz", result.token());
        assertEquals("alice", result.user().username().value());
        // 登录成功应刷新最近登录时间（> 0）。
        org.junit.jupiter.api.Assertions.assertEquals(true, result.user().lastLoginAt() > 0L,
                "成功登录应刷新 last_login_at 为正数 epoch 秒");

        verify(userRepository).save(any(User.class));
        verify(tokenIssuer).issue(any(User.class));
    }
}
