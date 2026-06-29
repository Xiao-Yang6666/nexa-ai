package com.nexa.application.routing;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;
import com.nexa.domain.routing.repository.AffinityCacheRepository;
import com.nexa.domain.routing.vo.AffinityCacheKey;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 亲和缓存用量统计查询用例（F-2033 + F-4014，PRD CH-4 / API-ENDPOINTS 5.4
 * GET /api/log/channel_affinity_usage_cache）。
 *
 * <p>领域规则来源：openapi /api/log/channel_affinity_usage_cache。入参 {@code rule_name + key_fp} 必填、
 * {@code using_group} 可选；按三元组精确查一条缓存的命中渠道用量（管理视图）。
 * {@code key_fp} 直接传会话键指纹（不需明文会话键），用 {@link AffinityCacheKey#ofFingerprint} 构造查询键。</p>
 *
 * <p>应用层薄编排：入参校验 + 委托 domain 仓储查询（backend-engineer §2.1）。</p>
 */
@Service
public class QueryAffinityUsageUseCase {

    private final AffinityCacheRepository cacheRepository;

    /**
     * @param cacheRepository 亲和缓存仓储（domain 端口）
     */
    public QueryAffinityUsageUseCase(AffinityCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /**
     * 查询用量统计。
     *
     * @param ruleName   规则名（必填）
     * @param keyFp      会话键指纹（必填，前端按 SHA-256 前 16 字节 hex 算法计算）
     * @param usingGroup 使用分组（可选）
     * @return 命中返回统计 Map（含 channel_id/hit_count/last_hit_at/expires_at），未命中空
     * @throws InvalidAffinityParameterException rule_name 或 key_fp 缺失
     */
    public Optional<Map<String, Object>> execute(String ruleName, String keyFp, String usingGroup) {
        if (ruleName == null || ruleName.isBlank()) {
            throw new InvalidAffinityParameterException("rule_name is required");
        }
        if (keyFp == null || keyFp.isBlank()) {
            throw new InvalidAffinityParameterException("key_fp is required");
        }
        AffinityCacheKey key = AffinityCacheKey.ofFingerprint(ruleName.trim(), keyFp.trim(),
                usingGroup == null || usingGroup.isBlank() ? null : usingGroup.trim());
        return cacheRepository.queryUsageStats(key);
    }
}
