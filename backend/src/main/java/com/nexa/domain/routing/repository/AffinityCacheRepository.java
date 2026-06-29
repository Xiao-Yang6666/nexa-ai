package com.nexa.domain.routing.repository;

import com.nexa.domain.routing.vo.AffinityCacheEntry;
import com.nexa.domain.routing.vo.AffinityCacheKey;

import java.util.Optional;

/**
 * 亲和缓存仓储接口（领域层定义，基础设施层实现，F-2029/F-2032/F-2033）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力（查询、回写/续期、清空、统计），不关心 JDBC/Redis/内存
 * （backend-engineer §2.3）。应用层用例仅依赖本接口，可在单测中用 mock 替换。
 * 实现见 {@code infrastructure.cache.*}。</p>
 *
 * <p>缓存键由三元组确定：{@code (rule_name, key_fingerprint, using_group)}（见
 * {@link AffinityCacheKey}）；缓存数据结构选型由 infra 决定（内存 LRU / 关系型 / Redis）。</p>
 */
public interface AffinityCacheRepository {

    /**
     * 查询缓存（F-2029，PRD CH-4 §3 af_cache）。
     *
     * <p>领域规则：查到且 {@link AffinityCacheEntry#isExpired} 未过期则命中粘连渠道；
     * 未查到或已过期返回空（回退普通选渠）。infra 实现可选在查询时清理过期条目（懒淘汰）。</p>
     *
     * @param key 缓存键（三元组）
     * @return 命中未过期返回条目，否则空
     */
    Optional<AffinityCacheEntry> find(AffinityCacheKey key);

    /**
     * 写入/续期缓存（F-2029/F-2031，PRD CH-4 §3 af_write「成功回写/续期」）。
     *
     * <p>领域规则：已存在相同 key 时覆盖（续期）；新键则写入（初次命中回写）。
     * 超出 maxEntries 上限时由 infra 决定淘汰策略（如 LRU）。</p>
     *
     * @param key   缓存键
     * @param entry 条目（含 channel_id/hitCount/lastHitAt/expiresAt）
     */
    void put(AffinityCacheKey key, AffinityCacheEntry entry);

    /**
     * 清空缓存（F-2032 全部清空）。
     *
     * @return 删除条数
     */
    long clearAll();

    /**
     * 按规则名清空缓存（F-2032 按 rule_name 清空）。
     *
     * @param ruleName 规则名
     * @return 删除条数
     */
    long clearByRule(String ruleName);

    /**
     * 查询特定会话键的用量统计（F-2033，入参 rule_name + key_fp + using_group 精确匹配一条缓存）。
     *
     * <p>返回 {@code Map} 含 {@code channel_id}/{@code hit_count}/{@code last_hit_at}/{@code expires_at}
     * 等统计信息（对齐 openapi additionalProperties:true schema，由调用方组装视图 DTO）。</p>
     *
     * @param key 缓存键（入参的三元组）
     * @return 命中返回统计 Map，否则空
     */
    Optional<java.util.Map<String, Object>> queryUsageStats(AffinityCacheKey key);
}
