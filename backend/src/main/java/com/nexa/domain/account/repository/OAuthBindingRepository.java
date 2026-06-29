package com.nexa.domain.account.repository;

import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.vo.OAuthProvider;

import java.util.List;
import java.util.Optional;

/**
 * OAuth 绑定仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>DDD 依赖倒置：domain 只声明需要的持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层 OAuth 用例仅依赖本接口，可在单测中用 mock 替换。实现见
 * {@code infrastructure.persistence.OAuthBindingRepositoryImpl}。</p>
 *
 * <p>关联表：DB-SCHEMA §13 {@code user_oauth_bindings}。</p>
 */
public interface OAuthBindingRepository {

    /**
     * 按 (provider, providerUserId) 反查绑定（OAuth 登录核心查询）。
     *
     * <p>OAuth 登录回调拿到第三方 userinfo 后，据此判断该第三方账号是否已绑定本站用户：
     * 命中 → 走登录（找到归属用户）；未命中 → 走建号 + 绑定（F-1016~1020）。
     * 对应 DB-SCHEMA §13 复合唯一 {@code ux_provider_userid (provider_id, provider_user_id)}，
     * 故至多一条。</p>
     *
     * @param provider       第三方 provider
     * @param providerUserId 第三方账号 id
     * @return 命中返回绑定，否则空
     */
    Optional<OAuthBinding> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    /**
     * 查询某用户在某 provider 下的绑定（绑定流程查重 / 解绑定位）。
     *
     * <p>对应 DB-SCHEMA §13 复合唯一 {@code ux_user_provider (user_id, provider_id)}，至多一条。</p>
     *
     * @param userId   本站用户 id
     * @param provider 第三方 provider
     * @return 命中返回绑定，否则空
     */
    Optional<OAuthBinding> findByUserIdAndProvider(long userId, OAuthProvider provider);

    /**
     * 持久化绑定（新增）。
     *
     * <p>新绑定保存后返回值携带数据库生成的自增 {@code id}。复合唯一索引冲突由实现层翻译为
     * {@link com.nexa.domain.account.exception.OAuthBindingConflictException}（不吞错）。</p>
     *
     * @param binding 待保存的绑定实体
     * @return 持久化后的绑定（含 id）
     * @throws com.nexa.domain.account.exception.OAuthBindingConflictException 唯一索引冲突
     */
    OAuthBinding save(OAuthBinding binding);

    /**
     * 列出某用户的全部 OAuth 绑定（管理端查询 F-1027 / 本人列表）。
     *
     * <p>对应 openapi {@code GET /api/user/{id}/oauth/bindings} 的数据来源。</p>
     *
     * @param userId 本站用户 id
     * @return 该用户的绑定列表（可能为空列表）
     */
    List<OAuthBinding> findByUserId(long userId);

    /**
     * 按 (userId, providerRefId) 查自定义 provider 绑定（按 provider_id 解绑定位，F-1026/1027）。
     *
     * <p>解绑端点 {@code {provider_id}} 为自定义 provider 整数主键；据此 + 归属用户定位待解绑的绑定，
     * 至多一条。内建 provider 绑定的 providerRefId 为 null，不会被本查询命中。</p>
     *
     * @param userId        本站用户 id
     * @param providerRefId 自定义 provider 整数主键
     * @return 命中返回绑定，否则空
     */
    Optional<OAuthBinding> findByUserIdAndProviderRefId(long userId, long providerRefId);

    /**
     * 删除一条绑定（解绑，F-1026 本人 / F-1027 管理端）。
     *
     * <p>领域语义：解绑后该 (provider, providerUserId) 不再能登录到原账号（PRD 验收标准）。
     * 删除按绑定主键 {@code id} 执行；调用方须先据归属校验确认可删（充血护栏在
     * {@link OAuthBinding#ensureOwnedBy} / 应用层）。</p>
     *
     * @param binding 待删除的绑定（须已持久化、含 id）
     */
    void delete(OAuthBinding binding);
}
