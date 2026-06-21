package com.nexa.account.application;

import com.nexa.account.application.port.AccountSettings;
import com.nexa.account.application.port.DomainEventPublisher;
import com.nexa.account.domain.event.UserRegistered;
import com.nexa.account.domain.exception.RegisterDisabledException;
import com.nexa.account.domain.exception.UserAlreadyExistsException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.PasswordHasher;
import com.nexa.account.domain.vo.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RegisterUserUseCase} 应用层单测（Mockito mock 仓储/端口，不起 Spring/DB）。
 *
 * <p>验证编排逻辑：注册开关闸门、用户名查重、落库、事件发布；业务不变量本身在 domain 已单测，
 * 这里聚焦"用例是否正确编排了协作者"。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterUserUseCase 注册编排")
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private AccountSettings settings;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private RegisterUserUseCase useCase;

    private RegisterUserCommand command;

    @BeforeEach
    void setUp() {
        command = new RegisterUserCommand("alice", "password123", "alice@example.com", null, null);
    }

    @Test
    @DisplayName("注册关闭：抛 RegisterDisabledException，不查重不落库")
    void register_disabled_throwsAndSkipsPersistence() {
        when(settings.isRegisterEnabled()).thenReturn(false);

        assertThrows(RegisterDisabledException.class, () -> useCase.register(command));

        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("用户名已存在：抛 UserAlreadyExistsException，不落库")
    void register_duplicateUsername_throws() {
        when(settings.isRegisterEnabled()).thenReturn(true);
        // 查重命中——查重在构造领域值对象之后、落库之前执行。
        when(userRepository.existsByUsername(Username.of("alice"))).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> useCase.register(command));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("正常注册：查重通过→落库→发布带 id 的注册事件")
    void register_success_savesAndPublishesEvent() {
        when(settings.isRegisterEnabled()).thenReturn(true);
        when(settings.quotaForNewUser()).thenReturn(500L);
        when(passwordHasher.hash(any())).thenReturn("HASHED");
        when(userRepository.existsByUsername(any())).thenReturn(false);

        // save 回填 id 后返回（模拟数据库生成主键）。
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.assignId(42L);
            return u;
        });

        User result = useCase.register(command);

        assertNotNull(result);
        assertEquals(42L, result.id());
        assertEquals("alice", result.username().value());
        assertEquals(500L, result.quota());

        // 发布的注册事件应带回填后的 id。
        ArgumentCaptor<UserRegistered> captor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(42L, captor.getValue().userId());
        assertEquals("alice", captor.getValue().username());
    }
}
