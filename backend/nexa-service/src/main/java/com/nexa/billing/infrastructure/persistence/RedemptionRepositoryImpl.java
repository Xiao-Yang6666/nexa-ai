package com.nexa.billing.infrastructure.persistence;

import com.nexa.shared.persistence.PageQueries;

import com.nexa.billing.domain.model.Redemption;
import com.nexa.billing.domain.repository.RedemptionRepository;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.billing.domain.vo.RedemptionStatus;
import com.nexa.billing.infrastructure.persistence.entity.RedemptionJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link RedemptionRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataRedemptionJpaRepository} +
 * 聚合↔实体映射实现（backend-engineer §2.3）。领域聚合 {@link Redemption} 与 JPA 实体分离，
 * 映射集中于此，domain 不感知 Hibernate。</p>
 */
@Repository
public class RedemptionRepositoryImpl implements RedemptionRepository {

    private final SpringDataRedemptionJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public RedemptionRepositoryImpl(SpringDataRedemptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Redemption> findByKey(String key) {
        return jpa.findByKey(key).map(RedemptionRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Redemption save(Redemption redemption) {
        RedemptionJpaEntity saved = jpa.save(toEntity(redemption));
        redemption.assignId(saved.getId());
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public List<Redemption> saveAll(List<Redemption> redemptions) {
        List<RedemptionJpaEntity> entities = redemptions.stream()
                .map(RedemptionRepositoryImpl::toEntity)
                .toList();
        return jpa.saveAll(entities).stream()
                .map(RedemptionRepositoryImpl::toDomain)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public Page<Redemption> findPage(int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.max(pageSize, 1);
        // 管理端列表按 id 降序（最新生成的码在前），结果稳定可分页。
        Pageable pageable = PageQueries.of(p, size, Sort.by(Sort.Direction.DESC, "id"));
        org.springframework.data.domain.Page<RedemptionJpaEntity> result = jpa.findAllBy(pageable);
        List<Redemption> items = result.getContent().stream()
                .map(RedemptionRepositoryImpl::toDomain)
                .toList();
        return new Page<>(items, result.getTotalElements(), p, size);
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param r 兑换码聚合
     * @return 待持久化的 JPA 实体
     */
    private static RedemptionJpaEntity toEntity(Redemption r) {
        RedemptionJpaEntity e = new RedemptionJpaEntity();
        e.setId(r.id());
        e.setUserId(r.creatorUserId());
        e.setKey(r.key());
        e.setStatus(r.status().code());
        e.setName(r.name());
        // quota 列为 integer（DB-SCHEMA §6），面额按现网整数语义；面额超 int 范围属配置错误，此处转 int。
        e.setQuota((int) r.quota().value());
        e.setCreatedTime(r.createdTime());
        e.setRedeemedTime(r.redeemedTime());
        e.setUsedUserId(r.usedUserId());
        e.setExpiredTime(r.expiredTime());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link Redemption#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的兑换码聚合
     */
    private static Redemption toDomain(RedemptionJpaEntity e) {
        // 过期时间 null→0（永不过期）的兜底已收敛进 Redemption.Builder.expiredTime，此处不再三元。
        return Redemption.builder()
                .id(e.getId())
                .creatorUserId(e.getUserId())
                .key(e.getKey() == null ? null : e.getKey().trim())  // char(32) 定长右补空格，去尾
                .status(RedemptionStatus.fromCode(e.getStatus() == null ? RedemptionStatus.UNUSED.code() : e.getStatus()))
                .name(e.getName())
                .quota(Quota.of(e.getQuota() == null ? 0L : e.getQuota()))
                .createdTime(e.getCreatedTime())
                .redeemedTime(e.getRedeemedTime())
                .usedUserId(e.getUsedUserId())
                .expiredTime(e.getExpiredTime())
                .build();
    }
}
