package com.nexa.infrastructure.routing.persistence;

import com.nexa.infrastructure.routing.persistence.mapper.AffinityCacheMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.routing.repository.AffinityCacheRepository;
import com.nexa.domain.routing.vo.AffinityCacheEntry;
import com.nexa.domain.routing.vo.AffinityCacheKey;
import com.nexa.infrastructure.routing.persistence.po.AffinityCachePO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 领域仓储 {@link AffinityCacheRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-2029/F-2032/F-2033）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link AffinityCacheMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现它。缓存条目按三元组 (rule_name, key_fingerprint, using_group)
 * 做逻辑唯一键，查询/更新走 findByTriplet（{@code selectOne}）/ save 实现 upsert。本表无软删除，清空/按规则名删
 * 走物理 {@code delete}，返回受影响行数。</p>
 *
 * <p>过期判定在领域层（{@link AffinityCacheEntry#isExpired}），本层不做懒淘汰——清理由 F-2032 管理端
 * 端点或后台定时任务执行（W2 不做定时清理，先保证正确）。</p>
 */
@Repository
public class AffinityCacheRepositoryImpl implements AffinityCacheRepository {

    private final AffinityCacheMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public AffinityCacheRepositoryImpl(AffinityCacheMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AffinityCacheEntry> find(AffinityCacheKey key) {
        return findByTriplet(key).map(AffinityCachePO::toDomain);
    }

    @Override
    @Transactional
    public void put(AffinityCacheKey key, AffinityCacheEntry entry) {
        // upsert：三元组命中则更新现有行（保留主键），否则插入新行。
        Optional<AffinityCachePO> existing = findByTriplet(key);
        AffinityCachePO po = AffinityCachePO.of(key, entry);
        if (existing.isPresent()) {
            po.setId(existing.get().getId());
            mapper.updateById(po);
        } else {
            mapper.insert(po);
        }
    }

    @Override
    @Transactional
    public long clearAll() {
        // 物理全表清空（F-2032 all=true），delete(null) 无 where 条件，返回受影响行数。
        return mapper.delete(null);
    }

    @Override
    @Transactional
    public long clearByRule(String ruleName) {
        // 物理按规则名清空（F-2032 rule_name 指定），返回受影响行数。
        return mapper.delete(Wrappers.<AffinityCachePO>lambdaQuery()
                .eq(AffinityCachePO::getRuleName, ruleName));
    }

    @Override
    public Optional<Map<String, Object>> queryUsageStats(AffinityCacheKey key) {
        return findByTriplet(key).map(e -> {
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

    /**
     * 按三元组 (rule_name, key_fingerprint, using_group) 查找单条缓存。
     *
     * <p>using_group 用 {@code IS NULL OR =} 兼容空值（PG 唯一索引用 COALESCE 表达式实现唯一）：
     * 入参为空查 {@code using_group IS NULL}，否则查 {@code using_group = ?}，等价于原 JPQL
     * {@code (:grp IS NULL AND c.usingGroup IS NULL) OR c.usingGroup = :grp}。</p>
     *
     * @param key 三元组缓存键
     * @return 命中返回 PO，否则空
     */
    private Optional<AffinityCachePO> findByTriplet(AffinityCacheKey key) {
        String grp = key.usingGroup();
        LambdaQueryWrapper<AffinityCachePO> w = Wrappers.<AffinityCachePO>lambdaQuery()
                .eq(AffinityCachePO::getRuleName, key.ruleName())
                .eq(AffinityCachePO::getKeyFingerprint, key.fingerprint())
                .and(c -> {
                    if (grp == null) {
                        c.isNull(AffinityCachePO::getUsingGroup);
                    } else {
                        c.eq(AffinityCachePO::getUsingGroup, grp);
                    }
                });
        return Optional.ofNullable(mapper.selectOne(w));
    }
}
