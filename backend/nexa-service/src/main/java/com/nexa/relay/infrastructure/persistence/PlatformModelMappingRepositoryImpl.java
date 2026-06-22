package com.nexa.relay.infrastructure.persistence;

import com.nexa.relay.domain.model.PlatformModelMapping;
import com.nexa.relay.domain.repository.PlatformModelMappingRepository;
import com.nexa.relay.infrastructure.persistence.entity.PlatformModelMappingJpaEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 超管底仓映射仓储 JPA 实现（基础设施层适配器，F-6011）。
 *
 * <p>DDD 依赖倒置：domain 定接口，本类用 {@link SpringDataPlatformModelMappingRepository} + 实体↔领域映射实现。
 * 软删除以 deleted_at epoch 标记（delete 不物理删行）。</p>
 */
@Repository("relayPlatformModelMappingRepositoryImpl")
public class PlatformModelMappingRepositoryImpl implements PlatformModelMappingRepository {

    private final SpringDataPlatformModelMappingRepository jpa;

    public PlatformModelMappingRepositoryImpl(SpringDataPlatformModelMappingRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<String> findUpstreamByPublicName(String publicName) {
        return jpa.findEnabledByPublicName(publicName).map(PlatformModelMappingJpaEntity::getUpstreamName);
    }

    @Override
    public Optional<PlatformModelMapping> findById(Long id) {
        return jpa.findActiveById(id).map(this::toDomain);
    }

    @Override
    public List<PlatformModelMapping> findAll() {
        return jpa.findAllActive().stream().map(this::toDomain).toList();
    }

    @Override
    public void save(PlatformModelMapping mapping) {
        PlatformModelMappingJpaEntity entity = mapping.id() == null
                ? new PlatformModelMappingJpaEntity()
                : jpa.findById(mapping.id()).orElseGet(PlatformModelMappingJpaEntity::new);
        entity.setPublicName(mapping.publicName());
        entity.setUpstreamName(mapping.upstreamName());
        entity.setEnabled(mapping.isEnabled());
        entity.setRemark(mapping.remark());
        entity.setCreatedTime(mapping.createdTime());
        entity.setUpdatedTime(mapping.updatedTime());
        jpa.save(entity);
    }

    @Override
    public void deleteById(Long id) {
        // 软删除：标记 deleted_at（不物理删，对齐现有软删除惯例）
        jpa.findActiveById(id).ifPresent(e -> {
            e.setDeletedAt(Instant.now().getEpochSecond());
            jpa.save(e);
        });
    }

    private PlatformModelMapping toDomain(PlatformModelMappingJpaEntity e) {
        return PlatformModelMapping.builder()
                .id(e.getId())
                .publicName(e.getPublicName())
                .upstreamName(e.getUpstreamName())
                .enabled(e.isEnabled())
                .remark(e.getRemark())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
