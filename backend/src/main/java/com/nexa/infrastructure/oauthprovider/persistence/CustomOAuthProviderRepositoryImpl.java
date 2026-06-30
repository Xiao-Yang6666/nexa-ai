package com.nexa.infrastructure.oauthprovider.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexa.domain.oauthprovider.exception.InvalidCustomOAuthProviderException;
import com.nexa.domain.oauthprovider.model.CustomOAuthProvider;
import com.nexa.domain.oauthprovider.repository.CustomOAuthProviderRepository;
import com.nexa.infrastructure.oauthprovider.persistence.po.CustomOAuthProviderPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link CustomOAuthProviderRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-1024/1025）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link CustomOAuthProviderMapper}
 * + PO 就近工厂方法（{@code PO.of} / {@code po.toDomain}）实现它。领域聚合 {@link CustomOAuthProvider}
 * 与 PO {@link CustomOAuthProviderPO} 分离，domain 因此不感知持久化框架（backend-engineer §2.3）。
 * name 唯一索引冲突翻译为领域异常（不吞错）。MyBatis-Plus 同样抛 Spring {@code DataIntegrityViolationException}。</p>
 */
@Repository
public class CustomOAuthProviderRepositoryImpl implements CustomOAuthProviderRepository {

    private final CustomOAuthProviderMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public CustomOAuthProviderRepositoryImpl(CustomOAuthProviderMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public CustomOAuthProvider save(CustomOAuthProvider provider) {
        CustomOAuthProviderPO po = CustomOAuthProviderPO.of(provider);
        try {
            if (po.getId() == null) {
                mapper.insert(po);          // 回填自增 id 到 po
            } else {
                mapper.updateById(po);
            }
            provider.assignId(po.getId());
            return po.toDomain();
        } catch (DataIntegrityViolationException e) {
            // name 唯一索引冲突：provider 重名，翻译为领域语义（不回显内部约束细节）。
            throw new InvalidCustomOAuthProviderException(
                    "custom oauth provider name already exists: " + provider.name());
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<CustomOAuthProvider> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(CustomOAuthProviderPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<CustomOAuthProvider> findByName(String name) {
        LambdaQueryWrapper<CustomOAuthProviderPO> w = Wrappers.<CustomOAuthProviderPO>lambdaQuery()
                .eq(CustomOAuthProviderPO::getName, name);
        return Optional.ofNullable(mapper.selectOne(w)).map(CustomOAuthProviderPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<CustomOAuthProvider> findAll() {
        return mapper.selectList(Wrappers.<CustomOAuthProviderPO>lambdaQuery()).stream()
                .map(CustomOAuthProviderPO::toDomain)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public void deleteById(long id) {
        mapper.deleteById(id);
    }
}
