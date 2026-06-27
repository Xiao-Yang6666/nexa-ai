package com.nexa.channel.infrastructure.persistence;

import com.nexa.shared.persistence.PageQueries;

import com.nexa.channel.domain.model.ChannelModelCost;
import com.nexa.channel.domain.repository.ChannelModelCostRepository;
import com.nexa.channel.domain.vo.Pagination;
import com.nexa.channel.infrastructure.persistence.entity.ChannelModelCostJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link ChannelModelCostRepository} 的 JPA 实现（基础设施层适配器，F-6006）。
 *
 * <p>DDD 依赖倒置落地。客户无任何读路径——仅 admin/root 应用层调用。软删除用 deleted_at。</p>
 */
@Repository
public class ChannelModelCostRepositoryImpl implements ChannelModelCostRepository {

    private final SpringDataChannelModelCostJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public ChannelModelCostRepositoryImpl(SpringDataChannelModelCostJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public ChannelModelCost save(ChannelModelCost cost) {
        ChannelModelCostJpaEntity saved = jpa.save(toEntity(cost));
        cost.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ChannelModelCost> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ChannelModelCost> findByChannelAndUpstream(int channelId, String upstreamModel) {
        if (upstreamModel == null || upstreamModel.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByChannelIdAndUpstreamModel(channelId, upstreamModel.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<ChannelModelCost> findPage(Integer channelId, String upstreamModel, Pagination pagination) {
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
        boolean hasChannel = channelId != null && channelId > 0;
        boolean hasUpstream = upstreamModel != null && !upstreamModel.isBlank();
        String b = hasUpstream ? upstreamModel.trim() : null;

        List<ChannelModelCostJpaEntity> rows;
        if (hasChannel && hasUpstream) {
            rows = jpa.findByBothPageOrdered(channelId, b, pageable);
        } else if (hasChannel) {
            rows = jpa.findByChannelIdPageOrdered(channelId, pageable);
        } else if (hasUpstream) {
            rows = jpa.findByUpstreamModelPageOrdered(b, pageable);
        } else {
            rows = jpa.findPageOrdered(pageable);
        }
        return rows.stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count(Integer channelId, String upstreamModel) {
        boolean hasChannel = channelId != null && channelId > 0;
        boolean hasUpstream = upstreamModel != null && !upstreamModel.isBlank();
        String b = hasUpstream ? upstreamModel.trim() : null;
        if (hasChannel && hasUpstream) {
            return jpa.countByChannelIdAndUpstreamModel(channelId, b);
        } else if (hasChannel) {
            return jpa.countByChannelId(channelId);
        } else if (hasUpstream) {
            return jpa.countByUpstreamModel(b);
        }
        return jpa.count();
    }

    /** {@inheritDoc} */
    @Override
    public List<ChannelModelCost> findByChannelsAndUpstreams(List<Integer> channelIds, List<String> upstreamModels) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        List<ChannelModelCost> byChannel = jpa.findByChannelIds(channelIds).stream().map(this::toDomain).toList();
        // upstreamModels 为可选 B 维过滤（供应渠道池按特定 B 查）；空集 → 不再按 B 过滤。
        if (upstreamModels == null || upstreamModels.isEmpty()) {
            return byChannel;
        }
        return byChannel.stream().filter(c -> upstreamModels.contains(c.upstreamModel())).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    // ---- 领域聚合 <-> JPA 实体映射 ----

    private ChannelModelCostJpaEntity toEntity(ChannelModelCost c) {
        ChannelModelCostJpaEntity e = new ChannelModelCostJpaEntity();
        e.setId(c.id());
        e.setChannelId(c.channelId());
        e.setUpstreamModel(c.upstreamModel());
        e.setCostRatio(c.costRatio());
        e.setCompletionCostRatio(c.completionCostRatio());
        e.setEnabled(c.enabled());
        e.setEffectiveTime(c.effectiveTime());
        e.setSourceUnitPrice(c.sourceUnitPrice());
        e.setRemark(c.remark());
        e.setCreatedTime(c.createdTime());
        e.setUpdatedTime(c.updatedTime());
        return e;
    }

    private ChannelModelCost toDomain(ChannelModelCostJpaEntity e) {
        return ChannelModelCost.builder()
                .id(e.getId())
                .channelId(e.getChannelId())
                .upstreamModel(e.getUpstreamModel())
                .costRatio(e.getCostRatio())
                .completionCostRatio(e.getCompletionCostRatio())
                .enabled(e.getEnabled())
                .effectiveTime(e.getEffectiveTime())
                .sourceUnitPrice(e.getSourceUnitPrice())
                .remark(e.getRemark())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
