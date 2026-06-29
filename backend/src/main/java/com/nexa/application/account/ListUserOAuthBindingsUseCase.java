package com.nexa.application.account;

import com.nexa.domain.account.exception.UserNotFoundException;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.repository.OAuthBindingRepository;
import com.nexa.domain.account.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端查询用户 OAuth 绑定用例（应用服务，F-1027）。
 *
 * <p>编排「管理员按目标用户 id 列出其全部 OAuth 绑定」（对齐 openapi {@code GET /api/user/{id}/oauth/bindings}）。
 * 本类<b>薄</b>：先校验目标用户存在（不存在 → 404），再经 {@link OAuthBindingRepository#findByUserId(long)}
 * 取绑定列表。无领域状态变更，故为只读查询（backend-engineer §2.1：应用层只编排，不含业务规则）。</p>
 *
 * <p>事务：标 {@code readOnly}，纯读路径，绑定列表与用户存在性在同一只读事务内一致快照。</p>
 *
 * <p>视图裁剪在接口层：本用例返回领域实体列表，接口层投影为 {@code OAuthBindingView}
 * （仅 id/provider_id/provider_user_id/created_at，<b>不</b>泄露任何敏感字段）。</p>
 */
@Service
public class ListUserOAuthBindingsUseCase {

    private final UserRepository userRepository;
    private final OAuthBindingRepository oauthBindingRepository;

    /**
     * @param userRepository         用户仓储（domain 接口，用于目标存在性校验）
     * @param oauthBindingRepository OAuth 绑定仓储（domain 接口）
     */
    public ListUserOAuthBindingsUseCase(UserRepository userRepository,
                                        OAuthBindingRepository oauthBindingRepository) {
        this.userRepository = userRepository;
        this.oauthBindingRepository = oauthBindingRepository;
    }

    /**
     * 列出某用户的全部 OAuth 绑定。
     *
     * @param targetUserId 目标用户 id（管理端路径段 {@code {id}}）
     * @return 该用户的绑定领域实体列表（可能为空列表）
     * @throws UserNotFoundException 目标用户不存在（404）
     */
    @Transactional(readOnly = true)
    public List<OAuthBinding> list(long targetUserId) {
        // 前置条件：目标用户须存在（对齐管理端 by-id 操作的统一前置，避免对不存在用户返回空列表造成误解）。
        if (userRepository.findById(targetUserId).isEmpty()) {
            throw new UserNotFoundException(targetUserId);
        }
        return oauthBindingRepository.findByUserId(targetUserId);
    }
}
