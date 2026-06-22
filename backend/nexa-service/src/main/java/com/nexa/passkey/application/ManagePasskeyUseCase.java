package com.nexa.passkey.application;

import com.nexa.passkey.domain.model.PasskeyCredential;
import com.nexa.passkey.domain.repository.PasskeyCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Passkey 查询/删除/管理端重置用例（应用服务，F-1031 本人查询+删除 / F-1032 管理端重置）。
 *
 * <p>承载 passkey 的非 ceremony 管理动作：
 * <ul>
 *   <li>{@link #findByUser(long)}：本人查询当前 passkey 状态（F-1031 GET，sessionAuth）。</li>
 *   <li>{@link #deleteSelf(long)}：本人删除 passkey（F-1031 DELETE，sessionAuth）。</li>
 *   <li>{@link #resetByAdmin(long)}：管理端重置目标用户 passkey（F-1032，adminAuth）。</li>
 * </ul>
 * 删除/重置均为幂等（目标无凭据时静默成功——重置语义对「本就没有」也算成功，避免误报失败）。
 * 对齐 openapi {@code /api/user/self/passkey}（GET/DELETE）与 {@code /api/user/{id}/reset_passkey}（DELETE）。</p>
 */
@Service
public class ManagePasskeyUseCase {

    private final PasskeyCredentialRepository repository;

    /**
     * @param repository passkey 凭据仓储
     */
    public ManagePasskeyUseCase(PasskeyCredentialRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询本人 passkey 状态（F-1031 GET）。
     *
     * <p>每用户至多一条 passkey（DB user_id 唯一），故返回 {@link Optional}；接口层据此组装
     * {@code {items: []}}（0 或 1 条），对齐 openapi 列表形态。</p>
     *
     * @param userId 会话用户 id
     * @return 命中返回凭据，否则空
     */
    @Transactional(readOnly = true)
    public Optional<PasskeyCredential> findByUser(long userId) {
        return repository.findByUserId(userId);
    }

    /**
     * 本人删除 passkey（F-1031 DELETE）。
     *
     * <p>幂等：本人无 passkey 时静默成功。删除后该用户不再具备无密码登录能力（回退密码/其他方式）。</p>
     *
     * @param userId 会话用户 id
     */
    @Transactional
    public void deleteSelf(long userId) {
        repository.deleteByUserId(userId);
    }

    /**
     * 管理端重置目标用户 passkey（F-1032）。
     *
     * <p>语义同删除（清除目标用户已注册的 passkey），用于用户丢失 authenticator 时由管理员解绑，
     * 使其可重新注册。幂等：目标无凭据静默成功。</p>
     *
     * @param targetUserId 目标用户 id
     */
    @Transactional
    public void resetByAdmin(long targetUserId) {
        repository.deleteByUserId(targetUserId);
    }
}
