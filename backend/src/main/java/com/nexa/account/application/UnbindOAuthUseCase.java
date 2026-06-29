package com.nexa.account.application;

import com.nexa.account.domain.exception.OAuthBindingNotFoundException;
import com.nexa.account.domain.exception.UserNotFoundException;
import com.nexa.account.domain.model.OAuthBinding;
import com.nexa.account.domain.repository.OAuthBindingRepository;
import com.nexa.account.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解绑用户 OAuth（自定义 provider）用例（应用服务，F-1026 本人 / F-1027 管理端）。
 *
 * <p>编排「按 (用户 id, 自定义 provider 整数 id) 定位绑定并删除」。本类<b>薄</b>：定位绑定（含归属语义）
 * → 充血归属校验 → 删除。归属护栏在领域实体 {@link OAuthBinding#ensureOwnedBy(long)} 守护（充血、禁贫血，
 * backend-engineer §2.1），应用层只编排事务边界。</p>
 *
 * <p>归属语义（安全）：定位用 {@code (userId, providerRefId)} 复合条件——本人解绑传入会话用户 id，管理端解绑
 * 传入路径目标用户 id。查询本身已把绑定限定在该用户名下（查不到即 404），无法解绑他人绑定。额外再过一道
 * {@link OAuthBinding#ensureOwnedBy} 作为充血护栏冗余兜底（防御式，规则不外泄到应用层）。</p>
 *
 * <p>对齐 openapi 端点：
 * <ul>
 *   <li>{@code DELETE /api/user/self/oauth/bindings/{provider_id}}（F-1026，sessionAuth）</li>
 *   <li>{@code DELETE /api/user/{id}/oauth/bindings/{provider_id}}（F-1027，adminAuth）</li>
 * </ul>
 * 解绑后该自定义 provider 账号不再能登录到原账号（PRD 解绑验收语义）。</p>
 *
 * <p>事务：定位 + 校验 + 删除在单事务内原子；未命中（{@link OAuthBindingNotFoundException}）回滚无副作用，
 * 解绑天然幂等（已不存在的绑定再解 → 404，不会误删他物）。</p>
 */
@Service
public class UnbindOAuthUseCase {

    private final UserRepository userRepository;
    private final OAuthBindingRepository oauthBindingRepository;

    /**
     * @param userRepository         用户仓储（domain 接口，管理端解绑用于目标存在性校验）
     * @param oauthBindingRepository OAuth 绑定仓储（domain 接口）
     */
    public UnbindOAuthUseCase(UserRepository userRepository,
                              OAuthBindingRepository oauthBindingRepository) {
        this.userRepository = userRepository;
        this.oauthBindingRepository = oauthBindingRepository;
    }

    /**
     * 本人解绑自定义 OAuth provider 绑定（F-1026）。
     *
     * <p>{@code userId} 为会话认证用户 id（接口层临时取自 {@code X-User-Id} 头，会话层接入后由认证主体提供）。
     * 仅能解绑本人名下绑定（查询已限定归属）。</p>
     *
     * @param userId        会话用户 id（解绑的归属用户）
     * @param providerRefId 自定义 provider 整数主键（端点 {@code {provider_id}}）
     * @throws OAuthBindingNotFoundException 该用户在该 provider 下无绑定（404）
     */
    @Transactional
    public void unbindSelf(long userId, long providerRefId) {
        // 本人解绑不校验用户存在性（会话用户必存在），直接按归属定位绑定。
        unbind(userId, providerRefId);
    }

    /**
     * 管理端解绑目标用户的自定义 OAuth provider 绑定（F-1027）。
     *
     * <p>先校验目标用户存在（不存在 → 404，对齐管理端 by-id 操作前置），再按归属定位绑定并删除。</p>
     *
     * @param targetUserId  目标用户 id（管理端路径段 {@code {id}}）
     * @param providerRefId 自定义 provider 整数主键（端点 {@code {provider_id}}）
     * @throws UserNotFoundException         目标用户不存在（404）
     * @throws OAuthBindingNotFoundException 目标用户在该 provider 下无绑定（404）
     */
    @Transactional
    public void unbindByAdmin(long targetUserId, long providerRefId) {
        // 管理端按 id 操作他人，先确认目标用户存在（与 ManageUserUseCase 一致的前置语义）。
        if (userRepository.findById(targetUserId).isEmpty()) {
            throw new UserNotFoundException(targetUserId);
        }
        unbind(targetUserId, providerRefId);
    }

    /**
     * 共享的定位 + 归属校验 + 删除逻辑（本人/管理端解绑复用）。
     *
     * @param userId        归属用户 id
     * @param providerRefId 自定义 provider 整数主键
     * @throws OAuthBindingNotFoundException 未命中绑定（404）
     */
    private void unbind(long userId, long providerRefId) {
        OAuthBinding binding = oauthBindingRepository.findByUserIdAndProviderRefId(userId, providerRefId)
                .orElseThrow(() -> new OAuthBindingNotFoundException(userId, providerRefId));
        // 充血归属护栏冗余兜底：查询已按 userId 限定，这里再确认一次（防御式，归属规则在领域内）。
        binding.ensureOwnedBy(userId);
        oauthBindingRepository.delete(binding);
    }
}
