package com.nexa.domain.oauthprovider.repository;

import com.nexa.domain.oauthprovider.model.CustomOAuthProvider;

import java.util.List;
import java.util.Optional;

/**
 * 自定义 OAuth provider 仓储接口（领域层定义，基础设施层实现，F-1024/1025）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，可在单测中用 mock 替换。实现见
 * {@code infrastructure.persistence.CustomOAuthProviderRepositoryImpl}。关联表：V4 {@code custom_oauth_providers}。</p>
 */
public interface CustomOAuthProviderRepository {

    /**
     * 保存（新增或更新）provider。
     *
     * <p>新建（id 为 null）保存后返回携带自增 id 的实体；name 唯一索引冲突由实现层翻译为
     * {@link com.nexa.domain.oauthprovider.exception.InvalidCustomOAuthProviderException}（不吞错）。</p>
     *
     * @param provider 待保存的 provider 聚合
     * @return 持久化后的 provider（新建含 id）
     */
    CustomOAuthProvider save(CustomOAuthProvider provider);

    /**
     * 按主键查 provider。
     *
     * @param id provider 主键（provider_id）
     * @return 命中返回聚合，否则空
     */
    Optional<CustomOAuthProvider> findById(long id);

    /**
     * 按 name（路由标识）查 provider（自定义 provider 登录路由解析用）。
     *
     * @param name provider 路由标识
     * @return 命中返回聚合，否则空
     */
    Optional<CustomOAuthProvider> findByName(String name);

    /**
     * 列出全部 provider（管理端列表，F-1024 GET）。
     *
     * @return provider 列表（可能为空列表）
     */
    List<CustomOAuthProvider> findAll();

    /**
     * 删除 provider（F-1024 DELETE）。
     *
     * @param id provider 主键
     */
    void deleteById(long id);
}
