package com.nexa.account.application;

import com.nexa.account.domain.exception.OAuthBindingNotFoundException;
import com.nexa.account.domain.exception.UserNotFoundException;
import com.nexa.account.domain.model.OAuthBinding;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.OAuthBindingRepository;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.OAuthProvider;
import com.nexa.account.domain.vo.Role;
import com.nexa.account.domain.vo.UserStatus;
import com.nexa.account.domain.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UnbindOAuthUseCase} 应用层单测（Mockito mock 仓储，不起 Spring/DB）。
 *
 * <p>覆盖解绑编排的本人（F-1026）/管理端（F-1027）两条路径 + 异常分支（backend-engineer §3.3）：
 * <ul>
 *   <li><b>本人解绑命中</b>：按 (userId, providerRefId) 命中绑定 → 归属校验通过 → 删除。</li>
 *   <li><b>本人解绑未命中</b>：抛 {@link OAuthBindingNotFoundException}（404），不调用 delete。</li>
 *   <li><b>管理端解绑命中</b>：目标用户存在 → 命中绑定 → 删除。</li>
 *   <li><b>管理端目标用户不存在</b>：抛 {@link UserNotFoundException}（404），不查/删绑定。</li>
 *   <li><b>管理端绑定未命中</b>：目标存在但无绑定 → 抛 {@link OAuthBindingNotFoundException}。</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnbindOAuthUseCase 解绑编排")
class UnbindOAuthUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OAuthBindingRepository oauthBindingRepository;

    /** 被测用例（显式构造注入）。 */
    private UnbindOAuthUseCase useCase() {
        return new UnbindOAuthUseCase(userRepository, oauthBindingRepository);
    }

    /** 构造一个落地态用户聚合（管理端路径用于目标存在性校验）。 */
    private User existingUser(long id) {
        return User.rehydrate(
                id, Username.of("u" + id), "STORED", null,
                Role.COMMON, UserStatus.ENABLED, 0L, "ABCD", 0L, 0L);
    }

    /** 构造一条属于指定用户的自定义 provider 绑定（重建态，含 id 与 providerRefId）。 */
    private OAuthBinding customBinding(long id, long userId, long providerRefId) {
        return OAuthBinding.rehydrate(
                id, userId, OAuthProvider.OIDC, "ext-user", providerRefId, Instant.now());
    }

    @Test
    @DisplayName("本人解绑命中：归属校验通过→删除")
    void unbindSelf_found_deletes() {
        OAuthBinding binding = customBinding(11L, 100L, 55L);
        when(oauthBindingRepository.findByUserIdAndProviderRefId(100L, 55L))
                .thenReturn(Optional.of(binding));

        useCase().unbindSelf(100L, 55L);

        verify(oauthBindingRepository).delete(binding);
    }

    @Test
    @DisplayName("本人解绑未命中：抛 OAuthBindingNotFoundException，不删除")
    void unbindSelf_notFound_throws() {
        when(oauthBindingRepository.findByUserIdAndProviderRefId(100L, 55L))
                .thenReturn(Optional.empty());

        assertThrows(OAuthBindingNotFoundException.class, () -> useCase().unbindSelf(100L, 55L));
        verify(oauthBindingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("管理端解绑命中：目标存在→命中绑定→删除")
    void unbindByAdmin_found_deletes() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(existingUser(100L)));
        OAuthBinding binding = customBinding(12L, 100L, 55L);
        when(oauthBindingRepository.findByUserIdAndProviderRefId(100L, 55L))
                .thenReturn(Optional.of(binding));

        useCase().unbindByAdmin(100L, 55L);

        verify(oauthBindingRepository).delete(binding);
    }

    @Test
    @DisplayName("管理端目标用户不存在：抛 UserNotFoundException，不查/删绑定")
    void unbindByAdmin_userNotFound_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> useCase().unbindByAdmin(999L, 55L));
        verify(oauthBindingRepository, never()).findByUserIdAndProviderRefId(eq(999L), eq(55L));
        verify(oauthBindingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("管理端目标存在但无绑定：抛 OAuthBindingNotFoundException")
    void unbindByAdmin_bindingNotFound_throws() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(existingUser(100L)));
        when(oauthBindingRepository.findByUserIdAndProviderRefId(100L, 55L))
                .thenReturn(Optional.empty());

        assertThrows(OAuthBindingNotFoundException.class, () -> useCase().unbindByAdmin(100L, 55L));
        verify(oauthBindingRepository, never()).delete(any());
    }
}
