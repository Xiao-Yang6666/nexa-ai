package com.nexa.infrastructure.account.persistence;

import com.nexa.domain.account.exception.OAuthBindingConflictException;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.repository.OAuthBindingRepository;
import com.nexa.domain.account.vo.OAuthProvider;
import com.nexa.infrastructure.account.persistence.entity.UserOAuthBindingJpaEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link OAuthBindingRepository} 的 JPA 实现（基础设施层适配器，F-1016~1027）。
 *
 * <p>DDD 依赖倒置落地：domain 定义 {@code OAuthBindingRepository} 接口，本类用
 * {@link SpringDataOAuthBindingJpaRepository} + 实体↔领域映射实现它（backend-engineer §2.3）。
 * 领域实体 {@link OAuthBinding} 与 JPA 实体 {@link UserOAuthBindingJpaEntity} 分离，映射集中在此处，
 * domain 因此不感知 Hibernate。</p>
 *
 * <p>内建 vs 自定义 provider 落库策略（V5 迁移）：
 * <ul>
 *   <li><b>内建 provider</b>（github/discord/oidc/linuxdo/wechat）：{@code provider} 列存
 *       {@link OAuthProvider#code()}，{@code provider_ref_id} 为 null。</li>
 *   <li><b>自定义 provider</b>（{@code com.nexa.oauthprovider} 的 CustomOAuthProvider，F-1025）：
 *       {@code provider_ref_id} 存其整数主键，{@code provider} 列存固定前缀
 *       {@code custom:<id>}（{@link OAuthBinding#CUSTOM_PROVIDER_CODE_PREFIX}），
 *       以保证复合唯一索引在不同自定义 provider 间天然区分。</li>
 * </ul>
 * 重建（toDomain）时据 {@code provider_ref_id} 是否非空判定走哪条 {@code rehydrate}。</p>
 *
 * <p>并发冲突兜底：建绑定并发竞态下，复合唯一索引（ux_provider_userid / ux_user_provider）在 {@code save}
 * 时抛 {@link DataIntegrityViolationException}，本类翻译为领域语义的 {@link OAuthBindingConflictException}
 * （不吞错，带 provider 上下文）。</p>
 */
@Repository
public class OAuthBindingRepositoryImpl implements OAuthBindingRepository {

    private final SpringDataOAuthBindingJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public OAuthBindingRepositoryImpl(SpringDataOAuthBindingJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthBinding> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId) {
        return jpa.findByProviderAndProviderUserId(provider.code(), providerUserId)
                .map(OAuthBindingRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthBinding> findByUserIdAndProvider(long userId, OAuthProvider provider) {
        return jpa.findByUserIdAndProvider(userId, provider.code())
                .map(OAuthBindingRepositoryImpl::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws OAuthBindingConflictException 当复合唯一索引冲突时（每 provider 一账号 / 每用户每 provider）
     */
    @Override
    public OAuthBinding save(OAuthBinding binding) {
        UserOAuthBindingJpaEntity entity = toEntity(binding);
        try {
            UserOAuthBindingJpaEntity saved = jpa.saveAndFlush(entity);
            // 保存后把数据库生成的 id 回填回领域实体。
            binding.assignId(saved.getId());
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底：并发建绑定竞态或第三方账号已绑到他人，翻译为领域异常（不回显对端敏感细节）。
            throw new OAuthBindingConflictException(providerLabel(binding));
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<OAuthBinding> findByUserId(long userId) {
        return jpa.findByUserId(userId).stream()
                .map(OAuthBindingRepositoryImpl::toDomain)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthBinding> findByUserIdAndProviderRefId(long userId, long providerRefId) {
        return jpa.findByUserIdAndProviderRefId(userId, providerRefId)
                .map(OAuthBindingRepositoryImpl::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>按绑定主键 {@code id} 删除（解绑，F-1026/F-1027）。未持久化（id 为 null）的绑定不应进入解绑流程，
     * 调用方在应用层已先查出再删，故此处直接按 id 删；id 缺失视为非法调用，抛 {@link IllegalArgumentException}
     * 而非静默忽略（不吞错）。</p>
     */
    @Override
    public void delete(OAuthBinding binding) {
        Long id = binding.id();
        if (id == null) {
            // 解绑流程必须先查后删，拿到的绑定一定带 id；id 为 null 是调用方编程错误，显式失败便于定位。
            throw new IllegalArgumentException("cannot delete an unpersisted oauth binding (id is null)");
        }
        jpa.deleteById(id);
    }

    // ---- 领域实体 <-> JPA 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 计算落库 {@code provider} 列的标识串：内建用枚举 code，自定义用 {@code custom:<id>}（V5 约定）。
     *
     * @param binding 领域绑定实体
     * @return 落库 provider 标识串
     */
    private static String providerColumn(OAuthBinding binding) {
        Long refId = binding.providerRefId();
        if (refId != null) {
            // 自定义 provider：provider 列存 custom:<id>，不同自定义 provider 天然区分，满足复合唯一索引语义。
            return OAuthBinding.CUSTOM_PROVIDER_CODE_PREFIX + refId;
        }
        return binding.provider().code();
    }

    /**
     * 冲突异常携带的 provider 标签（内建用枚举 code，自定义用 {@code custom:<id>}，不泄露上游敏感细节）。
     *
     * @param binding 领域绑定实体
     * @return provider 标签
     */
    private static String providerLabel(OAuthBinding binding) {
        return providerColumn(binding);
    }

    /**
     * 领域实体 → JPA 实体（持久化方向）。
     *
     * @param binding 领域绑定实体
     * @return 待持久化的 JPA 实体
     */
    private static UserOAuthBindingJpaEntity toEntity(OAuthBinding binding) {
        UserOAuthBindingJpaEntity e = new UserOAuthBindingJpaEntity();
        e.setId(binding.id());
        e.setUserId(binding.userId());
        e.setProvider(providerColumn(binding));
        e.setProviderUserId(binding.providerUserId());
        // 自定义 provider 的整数主键引用；内建 provider 为 null（对齐 V5 provider_ref_id 列）。
        e.setProviderRefId(binding.providerRefId());
        // 创建时间：领域实体已在 create() 打点 Instant，落库为 epoch 秒（DB-SCHEMA §13 created_at）。
        e.setCreatedAt(binding.createdAt() == null
                ? Instant.now().getEpochSecond()
                : binding.createdAt().getEpochSecond());
        return e;
    }

    /**
     * JPA 实体 → 领域实体（重建方向，走 {@link OAuthBinding#rehydrate}）。
     *
     * <p>据 {@code provider_ref_id} 是否非空区分两类绑定：非空走自定义 provider 重建（保留整数引用，
     * provider 枚举用 {@code provider} 列前缀对应的占位）；为空走内建 provider 重建（枚举由 provider 列解析）。</p>
     *
     * @param e JPA 实体
     * @return 重建的领域绑定实体
     */
    private static OAuthBinding toDomain(UserOAuthBindingJpaEntity e) {
        Instant createdAt = e.getCreatedAt() == null
                ? Instant.now()
                : Instant.ofEpochSecond(e.getCreatedAt());
        Long refId = e.getProviderRefId();
        if (refId != null) {
            // 自定义 provider 绑定：provider 列为 custom:<id>，不在内建枚举中。用占位枚举（OIDC）承载，
            // 领域侧以 providerRefId 区分自定义；客户/管理视图据 providerRefId 暴露整数 provider_id。
            return OAuthBinding.rehydrate(
                    e.getId(),
                    e.getUserId(),
                    OAuthProvider.OIDC,
                    e.getProviderUserId(),
                    refId,
                    createdAt);
        }
        return OAuthBinding.rehydrate(
                e.getId(),
                e.getUserId(),
                OAuthProvider.fromCode(e.getProvider()),
                e.getProviderUserId(),
                createdAt);
    }
}
