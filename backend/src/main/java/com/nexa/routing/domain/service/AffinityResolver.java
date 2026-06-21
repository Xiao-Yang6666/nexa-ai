package com.nexa.routing.domain.service;

import com.nexa.routing.domain.model.AffinityRule;
import com.nexa.routing.domain.repository.AffinityCacheRepository;
import com.nexa.routing.domain.vo.AffinityCacheEntry;
import com.nexa.routing.domain.vo.AffinityCacheKey;
import com.nexa.routing.domain.vo.AffinityDecision;
import com.nexa.routing.domain.vo.AffinityRequestContext;
import com.nexa.routing.domain.vo.AffinitySettings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话亲和解析领域服务（F-2029 键提取/粘连 + F-2030 header 透传 + F-2034 SkipRetryOnFailure，PRD CH-4）。
 *
 * <p>领域规则来源：FC-068 + PRD CH-4 §3「缓存命中分叉 + 成功才回写」闭环。本服务在<b>选渠前</b>对请求
 * 做亲和判定（{@link #resolve}），在<b>请求结束后</b>按结果回写缓存（{@link #onSuccess}）：
 * <ul>
 *   <li>resolve：按已启用规则有序匹配（model+path）；命中则提取会话键查缓存——命中未过期复用粘连渠道
 *       （af_stick），否则回退普通选渠（af_normal）。未命中任何规则=无亲和。</li>
 *   <li>onSuccess：请求成功且 {@code switchOnSuccess=true} 时回写/续期「会话键→渠道」映射（af_write）；
 *       switchOnSuccess=false 时不更新缓存（af_nowrite）。</li>
 * </ul>
 * 本服务为领域服务——跨亲和规则聚合 + 缓存仓储的业务逻辑，不属于单个聚合方法（backend-engineer §2.4）。
 * 零框架依赖，缓存仓储/请求上下文均为 domain 端口（依赖倒置），可纯单测。</p>
 */
public class AffinityResolver {

    private final AffinityCacheRepository cacheRepository;

    /**
     * @param cacheRepository 亲和缓存仓储（domain 端口，infra 实现）
     */
    public AffinityResolver(AffinityCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /**
     * 选渠前亲和判定（F-2029/F-2030/F-2034，PRD CH-4 §3 af_in→af_stick/af_normal）。
     *
     * <p>流程：
     * <ol>
     *   <li>settings 总开关关 → 无亲和（直走普通选渠）。</li>
     *   <li>按 enabledRules 有序找首个命中 (model+path) 的规则；无命中 → 无亲和。</li>
     *   <li>命中规则按 key_sources 提取会话键；提取失败 → 命中但无粘连（保留 header 透传/跳重试语义，回退选渠）。</li>
     *   <li>用 (rule, key_fp, using_group) 查缓存；命中未过期 → 粘连复用（sticky）；否则命中无粘连。</li>
     * </ol>
     * </p>
     *
     * @param model        请求模型名
     * @param path         请求 path
     * @param usingGroup   当前 token 使用分组（缓存键维度之一，可空）
     * @param request      请求上下文（提取会话键用）
     * @param enabledRules 已启用规则（有序，仓储 findEnabledRules 提供）
     * @param settings     亲和全局策略
     * @param now          当前时刻（注入便于单测）
     * @return 亲和判定结果
     */
    public AffinityDecision resolve(String model, String path, String usingGroup,
                                    AffinityRequestContext request, List<AffinityRule> enabledRules,
                                    AffinitySettings settings, Instant now) {
        if (settings == null || !settings.enabled()) {
            return AffinityDecision.noAffinity();
        }
        if (enabledRules == null || enabledRules.isEmpty()) {
            return AffinityDecision.noAffinity();
        }

        // 有序找首个命中规则（model + path AND 匹配）。
        AffinityRule matched = null;
        for (AffinityRule rule : enabledRules) {
            if (rule.matches(model, path)) {
                matched = rule;
                break;
            }
        }
        if (matched == null) {
            return AffinityDecision.noAffinity();
        }

        // 命中规则 → 提取会话键。
        String rawKey = matched.extractKey(request);
        if (rawKey == null || rawKey.isBlank()) {
            // 键提取失败：命中规则（保留 header 透传 + 跳重试），但无缓存键、回退普通选渠。
            return AffinityDecision.matchedNoStick(matched, null);
        }

        AffinityCacheKey cacheKey = new AffinityCacheKey(matched.name(), rawKey, usingGroup);

        // 查缓存：命中未过期则粘连复用。
        Optional<AffinityCacheEntry> hit = cacheRepository.find(cacheKey);
        if (hit.isPresent() && !hit.get().isExpired(now == null ? Instant.now() : now)) {
            AffinityCacheEntry entry = hit.get();
            // 续期一次命中（命中即视为有效使用，刷新 TTL + hitCount，PRD CH-4 用量统计 F-2033）。
            long ttl = matched.effectiveTtlSeconds(settings.defaultTtlSeconds());
            cacheRepository.put(cacheKey, entry.renew(now, ttl));
            return AffinityDecision.sticky(matched, cacheKey, entry.channelId());
        }

        // 缓存未命中或已过期：命中规则但无粘连（回退普通选渠，成功后回写）。
        return AffinityDecision.matchedNoStick(matched, cacheKey);
    }

    /**
     * 请求成功后回写缓存（F-2029/F-2031，PRD CH-4 §3 af_switch→af_write）。
     *
     * <p>领域规则：仅当命中规则、有缓存键、且 {@code switchOnSuccess=true} 时才把「会话键→实际成功渠道」
     * 回写/续期（switchOnSuccess=false → 不更新缓存 af_nowrite）。回写用实际成功渠道 id——
     * 若粘连复用成功则与原 channelId 相同（续期），若回退选渠选了新渠道则更新到新 channelId。</p>
     *
     * @param decision         resolve 阶段产出的判定
     * @param successChannelId 本次请求实际成功的渠道 id
     * @param settings         亲和全局策略（读 switchOnSuccess + 默认 TTL）
     * @param now              当前时刻
     */
    public void onSuccess(AffinityDecision decision, long successChannelId,
                          AffinitySettings settings, Instant now) {
        if (decision == null || !decision.hasMatch() || decision.cacheKey() == null) {
            return;
        }
        if (settings == null || !settings.switchOnSuccess()) {
            // switchOnSuccess=false：不更新缓存（PRD CH-4 §3 af_nowrite）。
            return;
        }
        long ttl = decision.matchedRule().effectiveTtlSeconds(settings.defaultTtlSeconds());
        Instant n = now == null ? Instant.now() : now;
        Optional<AffinityCacheEntry> existing = cacheRepository.find(decision.cacheKey());
        AffinityCacheEntry next;
        if (existing.isPresent() && existing.get().channelId() == successChannelId) {
            // 同渠道续期（hitCount 累加）。
            next = existing.get().renew(n, ttl);
        } else {
            // 新渠道（首次回写或回退选渠选了别的渠道）→ 新建条目。
            next = AffinityCacheEntry.firstHit(successChannelId, n, ttl);
        }
        cacheRepository.put(decision.cacheKey(), next);
    }
}
