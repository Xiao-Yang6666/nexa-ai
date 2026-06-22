package com.nexa.model.application.port;

/**
 * 定价刷新端口（应用层 / 防腐层接口，F-3015/F-3016/F-3017 副作用 RefreshPricing()）。
 *
 * <p>领域规则来源：PRD ML-1。模型元数据创建/更新/删除后须刷新对外定价缓存。真实现涉及对外目录/
 * 定价聚合重算（属计费/定价上下文），本片用占位实现承载副作用契约（切片边界诚实标注），
 * 真实接入仅替换基础设施层实现，应用层用例无需改动（DDD 防腐层价值）。</p>
 */
public interface RefreshPricingPort {

    /**
     * 触发定价缓存刷新（幂等，可重复调用）。
     */
    void refresh();
}
