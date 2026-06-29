package com.nexa.application.routing;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;
import com.nexa.domain.routing.repository.AffinityCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清空渠道亲和缓存用例（F-2032，PRD CH-4 / API-ENDPOINTS 5.4 POST /api/channel_affinity_cache/clear）。
 *
 * <p>领域规则来源：openapi /api/channel_affinity_cache/clear。{@code all=true} 清空全部；
 * {@code rule_name} 指定清空某条规则的缓存；二者必须有且仅有一个（互斥但必选），都缺失报 400。</p>
 *
 * <p>应用层薄编排：入参校验 + 委托 domain 仓储执行，不含业务规则（backend-engineer §2.1 应用层薄）。</p>
 */
@Service
public class ClearAffinityCacheUseCase {

    private final AffinityCacheRepository cacheRepository;

    /**
     * @param cacheRepository 亲和缓存仓储（domain 端口）
     */
    public ClearAffinityCacheUseCase(AffinityCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /**
     * 执行清空。
     *
     * @param all      true=清全部（与 ruleName 互斥）
     * @param ruleName 规则名（非空时按规则清；与 all 互斥）
     * @return 删除条数
     * @throws InvalidAffinityParameterException all 与 ruleName 都缺失
     */
    @Transactional
    public long execute(Boolean all, String ruleName) {
        boolean hasAll = Boolean.TRUE.equals(all);
        boolean hasRule = ruleName != null && !ruleName.isBlank();
        if (!hasAll && !hasRule) {
            throw new InvalidAffinityParameterException("either all=true or rule_name must be provided");
        }
        if (hasAll) {
            return cacheRepository.clearAll();
        }
        return cacheRepository.clearByRule(ruleName.trim());
    }
}
