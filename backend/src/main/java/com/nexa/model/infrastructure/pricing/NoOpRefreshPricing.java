package com.nexa.model.infrastructure.pricing;

import com.nexa.model.application.port.RefreshPricingPort;
import org.springframework.stereotype.Component;

/**
 * 定价刷新端口的占位实现（基础设施层 stub adapter，F-3015/F-3016/F-3017 RefreshPricing()）。
 *
 * <p><b>切片边界说明（诚实标注）</b>：模型元数据增删改后须刷新对外定价缓存（PRD ML-1）。真实定价
 * 重算属对外目录/定价上下文（PublicModel + 倍率聚合），跨上下文。本片先以空操作承载副作用契约，
 * 保证模型 CRUD 用例的副作用编排路径完整、可单测（mock 本端口验证被调用）。真实接入仅替换本
 * adapter，应用层用例无需改动（DDD 防腐层价值）。</p>
 */
@Component
public class NoOpRefreshPricing implements RefreshPricingPort {

    /** {@inheritDoc} */
    @Override
    public void refresh() {
        // 占位：空操作。真实现触发对外定价缓存重算（跨定价上下文）。
    }
}
