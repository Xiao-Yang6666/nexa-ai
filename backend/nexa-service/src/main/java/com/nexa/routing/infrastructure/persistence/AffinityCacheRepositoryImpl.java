package com.nexa.routing.infrastructure.persistence;

import com.nexa.routing.domain.repository.AffinityCacheRepository;
import com.nexa.routing.domain.vo.AffinityCacheEntry;
import com.nexa.routing.domain.vo.AffinityCacheKey;
import com.nexa.routing.infrastructure.persistence.entity.AffinityCacheJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 领域仓储 {@link AffinityCacheRepository} 的 JPA 实现（基础设施层适配器，F-2029/F-2032/F-2033）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataAffinityCacheJpaRepository} +
 * 实体↔领域映射实现它。缓存条目按三元组 (rule_name, key_fingerprint, using_group) 做逻辑唯一键，
 * 查询/更新走 findByTriplet / save 实现 upsert。</p>
 *
 * <p>过期判定在领域层（{@link AffinityCacheEntry#isExpired}），本层不做懒淘汰——清理由 F-2032 管理端
 * 端点或后台定时任务执行（W2 不做定时清理，先保证正确）。</p>
 */
@Repository
public class AffinityCacheRepositoryImpl implements AffinityCacheRepository {

    private final SpringDataAffinityCacheJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 缓存仓库
     */
    public AffinityCacheRepositoryImpl(SpringDataAffinityCacheJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<AffinityCacheEntry> find(AffinityCacheKey key) {
        String fp = key.fingerprint();
        return jpa.findByTriplet(key.ruleName(), fp, key.usingGroup())
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public void put(AffinityCacheKey key, AffinityCacheEntry entry) {
        String fp = key.fingerprint();
        AffinityCacheJpaEntity entity = jpa.findByTriplet(key.ruleName(), fp, key.usingGroup())
                .orElseGet(AffinityCacheJpaEntity::new);
        entity.setRuleName(key.ruleName());
        entity.setKeyFingerprint(fp);
        entity.setUsingGroup(key.usingGroup());
        entity.setChannelId(entry.channelId());
        entity.setHitCount(entry.hitCount());
        entity.setLastHitAt(entry.lastHitAt().getEpochSecond());
        entity.setExpiresAt(entry.expiresAt().getEpochSecond());
        jpa.save(entity);
    }

    @Override
    @Transactional
    public long clearAll() {
        return jpa.deleteAllEntries();
    }

    @Override
    @Transactional
    public long clearByRule(String ruleName) {
        return jpa.deleteByRuleName(ruleName);
    }

    @Override
    public Optional<Map<String, Object>> queryUsageStats(AffinityCacheKey key) {
        String fp = key.fingerprint();
        return jpa.findByTriplet(key.ruleName(), fp, key.usingGroup())
                .map(e -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("rule_name", e.getRuleName());
                    stats.put("key_fingerprint", e.getKeyFingerprint());
                    stats.put("using_group", e.getUsingGroup());
                    stats.put("channel_id", e.getChannelId());
                    stats.put("hit_count", e.getHitCount());
                    stats.put("last_hit_at", e.getLastHitAt());
                    stats.put("expires_at", e.getExpiresAt());
                    return stats;
                });
    }

    private AffinityCacheEntry toDomain(AffinityCacheJpaEntity e) {
        return new AffinityCacheEntry(
                e.getChannelId(), e.getHitCount(),
                Instant.ofEpochSecond(e.getLastHitAt()),
                Instant.ofEpochSecond(e.getExpiresAt()));
    }
}
