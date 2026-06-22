package com.nexa.model.application;

import com.nexa.model.domain.model.PublicModel;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.model.interfaces.api.dto.PricingPublicView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开模型价格页查询用例（应用层，F-2048 / ML-4）。
 *
 * <p>承载「公开价格页」只读编排：取上架对外模型全集 → 逐字段裁剪为零泄露 PublicView → 附
 * 可用分组倍率/自动分组/价格版本元信息。本端点公开（{@code security: []}，匿名可访问），
 * 故走 ML-4「匿名取公开可用分组」分支——匿名仅见公开分组 {@code free}（BL-8 free=1.0），
 * {@code group_ratio} 只保留可用分组（绝不含不可用分组，prd-model ML-4 §6 验收）。</p>
 *
 * <p>DDD：薄应用层（{@link Transactional}(readOnly) 只读事务），业务读经 {@link PublicModelRepository}，
 * 不含领域规则；零泄露投影由 {@link PricingPublicView} 接口层 DTO 守护。</p>
 */
@Service
public class QueryPublicPricingUseCase {

    /**
     * 价格版本常量（ML-4 {@code pricing_version}）。前端用于缓存校验/对账；价格表结构或口径
     * 升级时手动 bump。来源：FC-065 GetPricing 的 pricing_version 常量语义。
     */
    private static final String PRICING_VERSION = "v1";

    /**
     * 公开（匿名）可用分组名（ML-4「匿名取公开可用分组」）。匿名用户仅可见基础公开分组 {@code free}。
     * 来源：COMPAT-BILLING-DECISIONS BL-8 分组折扣（free=1.0/vip=0.85/svip=0.7）的公开子集。
     */
    private static final String PUBLIC_GROUP = "free";

    /**
     * 公开分组折扣系数（BL-8 free=1.0：基础分组无折扣）。匿名价格页只暴露该公开分组倍率，
     * 不泄露 vip/svip 等需登录分组的折扣（ML-4 §6：group_ratio 只含当前可用分组）。
     */
    private static final BigDecimal PUBLIC_GROUP_RATIO = BigDecimal.ONE;

    private final PublicModelRepository repository;

    /** @param repository 对外模型商品目录仓储（定价主体来源） */
    public QueryPublicPricingUseCase(PublicModelRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询公开价格页（F-2048，匿名公开视图）。
     *
     * <p>定价主体 = 上架对外模型全集（{@code enabled=true}，ML-4「仅启用模型进入结果」）；
     * 逐条裁剪为零泄露 {@link PricingPublicView.Item}。可用分组为空时仍返回元信息（不报错，
     * ML-4 §6「可用分组为空 → 返回空定价列表，不报错」——此处公开分组恒含 free，故非空）。</p>
     *
     * @param locale 展示语言（ML-4 元信息入参，预留；当前商品目录字段与语言无关，暂不分支）
     * @return 公开价格页视图（PublicView 零泄露）
     */
    @Transactional(readOnly = true)
    public PricingPublicView query(String locale) {
        List<PricingPublicView.Item> items = repository.findAllEnabled().stream()
                .map(PricingPublicView.Item::from)
                .toList();

        // 仅保留可用分组的倍率（匿名 → 公开分组 free）。LinkedHashMap 保稳定顺序，便于前端/快照对账。
        Map<String, BigDecimal> groupRatio = new LinkedHashMap<>();
        groupRatio.put(PUBLIC_GROUP, PUBLIC_GROUP_RATIO);

        // auto_groups：自动分组列表（ML-4 元信息）。匿名公开口径下仅公开分组可自动命中。
        List<String> autoGroups = List.of(PUBLIC_GROUP);

        return new PricingPublicView(items, groupRatio, autoGroups, PRICING_VERSION);
    }
}
