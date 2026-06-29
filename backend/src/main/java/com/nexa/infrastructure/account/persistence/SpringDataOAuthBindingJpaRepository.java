package com.nexa.infrastructure.account.persistence;

import com.nexa.infrastructure.account.persistence.po.UserOAuthBindingPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 仓库（OAuth 绑定，基础设施层内部接口）。
 *
 * <p>仅供 {@link OAuthBindingRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.repository.OAuthBindingRepository}。派生查询对齐复合唯一约束
 * {@code ux_provider_userid (provider, provider_user_id)} 与 {@code ux_user_provider (user_id, provider)}。</p>
 */
interface SpringDataOAuthBindingJpaRepository extends JpaRepository<UserOAuthBindingPO, Long> {

    /**
     * 按 (provider, providerUserId) 反查绑定（OAuth 登录核心查询）。
     *
     * @param provider       provider 标识串
     * @param providerUserId 第三方账号 id
     * @return 命中实体，否则空（复合唯一，至多一条）
     */
    Optional<UserOAuthBindingPO> findByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * 按 (userId, provider) 查绑定（绑定查重 / 解绑定位）。
     *
     * @param userId   本站用户 id
     * @param provider provider 标识串
     * @return 命中实体，否则空（复合唯一，至多一条）
     */
    Optional<UserOAuthBindingPO> findByUserIdAndProvider(Long userId, String provider);

    /**
     * 列出某用户的全部绑定（管理端查询 F-1027 / 本人查询）。
     *
     * @param userId 本站用户 id
     * @return 绑定列表（按建表顺序，可能为空）
     */
    List<UserOAuthBindingPO> findByUserId(Long userId);

    /**
     * 按 (userId, providerRefId) 查自定义 provider 绑定（按 provider_id 解绑定位，F-1026/1027）。
     *
     * <p>自定义 provider 的绑定由整数 {@code provider_ref_id} 标识（openapi 解绑端点的 {@code {provider_id}}）。
     * 复合上 user_id 限定归属，至多一条。</p>
     *
     * @param userId        本站用户 id
     * @param providerRefId 自定义 provider 整数主键
     * @return 命中实体，否则空
     */
    Optional<UserOAuthBindingPO> findByUserIdAndProviderRefId(Long userId, Long providerRefId);
}
