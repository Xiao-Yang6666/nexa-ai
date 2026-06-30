package com.nexa.infrastructure.account.persistence;

import com.nexa.infrastructure.account.persistence.mapper.OAuthBindingMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.account.exception.OAuthBindingConflictException;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.repository.OAuthBindingRepository;
import com.nexa.domain.account.vo.OAuthProvider;
import com.nexa.infrastructure.account.persistence.po.UserOAuthBindingPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link OAuthBindingRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-1016~1027）。
 *
 * <p>DDD 依赖倒置落地：domain 定义 {@code OAuthBindingRepository} 接口，本类用 {@link OAuthBindingMapper} +
 * PO 就近工厂方法（{@code PO.of} / {@code po.toDomain}）实现它。领域实体 {@link OAuthBinding} 与 PO
 * {@link UserOAuthBindingPO} 分离，映射收敛在 PO，domain 因此不感知 MyBatis-Plus。</p>
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
 * 落库 provider 列计算与重建判定均收敛在 {@link UserOAuthBindingPO} 的就近工厂方法。</p>
 *
 * <p>并发冲突兜底：建绑定并发竞态下，复合唯一索引（ux_provider_userid / ux_user_provider）在 {@code save}
 * 时抛 {@link DataIntegrityViolationException}，本类翻译为领域语义的 {@link OAuthBindingConflictException}
 * （不吞错，带 provider 上下文）。</p>
 */
@Repository
public class OAuthBindingRepositoryImpl implements OAuthBindingRepository {

    private final OAuthBindingMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public OAuthBindingRepositoryImpl(OAuthBindingMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthBinding> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId) {
        // (provider, provider_user_id) 复合唯一，至多一条。
        LambdaQueryWrapper<UserOAuthBindingPO> w = Wrappers.<UserOAuthBindingPO>lambdaQuery()
                .eq(UserOAuthBindingPO::getProvider, provider.code())
                .eq(UserOAuthBindingPO::getProviderUserId, providerUserId);
        return Optional.ofNullable(mapper.selectOne(w)).map(UserOAuthBindingPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthBinding> findByUserIdAndProvider(long userId, OAuthProvider provider) {
        // (user_id, provider) 复合唯一，至多一条。
        LambdaQueryWrapper<UserOAuthBindingPO> w = Wrappers.<UserOAuthBindingPO>lambdaQuery()
                .eq(UserOAuthBindingPO::getUserId, userId)
                .eq(UserOAuthBindingPO::getProvider, provider.code());
        return Optional.ofNullable(mapper.selectOne(w)).map(UserOAuthBindingPO::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws OAuthBindingConflictException 当复合唯一索引冲突时（每 provider 一账号 / 每用户每 provider）
     */
    @Override
    public OAuthBinding save(OAuthBinding binding) {
        UserOAuthBindingPO po = UserOAuthBindingPO.of(binding);
        try {
            mapper.insert(po);              // 回填自增 id 到 po
            // 保存后把数据库生成的 id 回填回领域实体。
            binding.assignId(po.getId());
            return po.toDomain();
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底：并发建绑定竞态或第三方账号已绑到他人，翻译为领域异常（不回显对端敏感细节）。
            throw new OAuthBindingConflictException(providerLabel(binding));
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<OAuthBinding> findByUserId(long userId) {
        LambdaQueryWrapper<UserOAuthBindingPO> w = Wrappers.<UserOAuthBindingPO>lambdaQuery()
                .eq(UserOAuthBindingPO::getUserId, userId);
        return mapper.selectList(w).stream()
                .map(UserOAuthBindingPO::toDomain)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthBinding> findByUserIdAndProviderRefId(long userId, long providerRefId) {
        // (user_id, provider_ref_id) 定位自定义 provider 绑定，至多一条。
        LambdaQueryWrapper<UserOAuthBindingPO> w = Wrappers.<UserOAuthBindingPO>lambdaQuery()
                .eq(UserOAuthBindingPO::getUserId, userId)
                .eq(UserOAuthBindingPO::getProviderRefId, providerRefId);
        return Optional.ofNullable(mapper.selectOne(w)).map(UserOAuthBindingPO::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>按绑定主键 {@code id} 删除（解绑，F-1026/F-1027）。未持久化（id 为 null）的绑定不应进入解绑流程，
     * 调用方在应用层已先查出再删，故此处直接按 id 删；id 缺失视为非法调用，抛 {@link IllegalArgumentException}
     * 而非静默忽略（不吞错）。本表无软删除，物理删除。</p>
     */
    @Override
    public void delete(OAuthBinding binding) {
        Long id = binding.id();
        if (id == null) {
            // 解绑流程必须先查后删，拿到的绑定一定带 id；id 为 null 是调用方编程错误，显式失败便于定位。
            throw new IllegalArgumentException("cannot delete an unpersisted oauth binding (id is null)");
        }
        mapper.deleteById(id);
    }

    // ---- 冲突异常 provider 标签计算（落库列计算与映射收敛在 PO 工厂方法） ----

    /**
     * 冲突异常携带的 provider 标签（内建用枚举 code，自定义用 {@code custom:<id>}，不泄露上游敏感细节）。
     *
     * @param binding 领域绑定实体
     * @return provider 标签
     */
    private static String providerLabel(OAuthBinding binding) {
        Long refId = binding.providerRefId();
        if (refId != null) {
            return OAuthBinding.CUSTOM_PROVIDER_CODE_PREFIX + refId;
        }
        return binding.provider().code();
    }
}
