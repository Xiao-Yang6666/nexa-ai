package com.nexa.application.publicsite;

import com.nexa.application.publicsite.port.PublicSiteSettings;
import com.nexa.domain.publicsite.vo.SiteStatus;
import org.springframework.stereotype.Service;

/**
 * 查询营销首页公开状态聚合用例（应用服务，F-4039）。
 *
 * <p>编排极薄：从 {@link PublicSiteSettings} 端口取出公开站点状态聚合并返回（backend-engineer §2.1
 * application 薄、不含领域规则）。状态聚合的「零敏感字段」不变量由领域值对象 {@link SiteStatus} 的字段集合
 * 与实现端口共同保证——本用例不接触任何敏感配置。</p>
 *
 * <p>对齐 openapi {@code GET /api/status}（security: []，公开），出参 {@code StatusAggregateView}。</p>
 */
@Service
public class QuerySiteStatusUseCase {

    private final PublicSiteSettings settings;

    /**
     * @param settings 公开站点设置端口（infra 提供，只暴露公开安全字段）
     */
    public QuerySiteStatusUseCase(PublicSiteSettings settings) {
        this.settings = settings;
    }

    /**
     * 读取公开站点状态聚合（F-4039）。
     *
     * @return 站点状态聚合值对象（不含任何敏感字段）
     */
    public SiteStatus query() {
        return settings.siteStatus();
    }
}
