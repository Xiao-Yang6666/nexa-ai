package com.nexa.application.model;

import com.nexa.application.model.port.RefreshPricingPort;
import com.nexa.domain.model.exception.InvalidModelParameterException;
import com.nexa.domain.model.exception.PublicModelNotFoundException;
import com.nexa.domain.model.model.PublicModel;
import com.nexa.domain.model.repository.PublicModelRepository;
import com.nexa.domain.model.vo.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 对外模型商品目录 CRUD 用例（应用层，F-6001/F-6004）。
 *
 * <p>承载列表/创建/更新/删除全部用例。领域不变量（A 非空白/价格非负）在 {@link PublicModel} 聚合守护，
 * 跨聚合唯一性（A 全局唯一）在本用例校验（backend-engineer §2.2）。</p>
 *
 * <p>F-6004 模型权限全开：上架（{@code enabled=true}）即全员可用；本用例无独立权限端点，
 * 通过 {@link PublicModel#update} 的 enabled 切换实现上下架。</p>
 */
@Service
public class ManagePublicModelUseCase {

    private final PublicModelRepository repository;
    private final RefreshPricingPort refreshPricing;

    /**
     * @param repository     对外模型仓储
     * @param refreshPricing 定价缓存刷新端口（PublicModel 改价后必须触发，F-6001 对齐 ML-1）
     */
    public ManagePublicModelUseCase(PublicModelRepository repository, RefreshPricingPort refreshPricing) {
        this.repository = repository;
        this.refreshPricing = refreshPricing;
    }

    /**
     * 分页列表（F-6001）。
     *
     * @param pagination  分页参数
     * @param enabledOnly true=仅上架；false=全部
     * @return 当前页对外模型
     */
    @Transactional(readOnly = true)
    public List<PublicModel> list(Pagination pagination, boolean enabledOnly) {
        return repository.findPage(pagination, enabledOnly);
    }

    /**
     * 总条数（F-6001 列表 total）。
     *
     * @param enabledOnly true=仅上架；false=全部
     * @return 满足条件的总数
     */
    @Transactional(readOnly = true)
    public long count(boolean enabledOnly) {
        return repository.count(enabledOnly);
    }

    /**
     * 创建对外模型（F-6001）。
     *
     * @param publicName     A
     * @param basePriceRatio 基准售价倍率（可空 → 0）
     * @param usePrice       是否按次定价
     * @param basePrice      固定单价
     * @param enabled        是否上架（可空 → true）
     * @param displayName    展示名
     * @param sortOrder      排序
     * @param description    描述
     * @return 创建后的对外模型
     * @throws InvalidModelParameterException A 为空（领域）或 A 已存在（本用例）
     */
    @Transactional
    public PublicModel create(String publicName, BigDecimal basePriceRatio,
                              Boolean usePrice, BigDecimal basePrice, Boolean enabled,
                              String displayName, Integer sortOrder, String description) {
        PublicModel model = PublicModel.create(publicName, basePriceRatio,
                usePrice, basePrice, enabled, displayName, sortOrder, description);
        // 跨聚合不变量：A 全局唯一（uk_public_name）。重名 → 「对外模型名已存在」（F-6001）。
        repository.findByPublicName(model.publicName()).ifPresent(existing -> {
            throw new InvalidModelParameterException("对外模型名已存在");
        });
        PublicModel saved = repository.save(model);
        refreshPricing.refresh();
        return saved;
    }

    /**
     * 更新对外模型（F-6001 含上下架）。
     *
     * @param id             对外模型 id
     * @param basePriceRatio 新基准售价倍率（可空 → 不改）
     * @param usePrice       新按次定价开关（可空 → 不改）
     * @param basePrice      新固定单价（可空 → 不改）
     * @param enabled        新上下架（可空 → 不改；F-6004 上架即全员可用）
     * @param displayName    新展示名（可空 → 不改）
     * @param sortOrder      新排序（可空 → 不改）
     * @param description    新描述（可空 → 不改）
     * @return 更新后的对外模型
     * @throws InvalidModelParameterException 缺 id / 价格非法
     * @throws PublicModelNotFoundException   不存在
     */
    @Transactional
    public PublicModel update(Long id, BigDecimal basePriceRatio,
                              Boolean usePrice, BigDecimal basePrice, Boolean enabled,
                              String displayName, Integer sortOrder, String description) {
        if (id == null || id <= 0) {
            throw new InvalidModelParameterException("缺少对外模型 ID");
        }
        Optional<PublicModel> existing = repository.findById(id);
        PublicModel model = existing.orElseThrow(() -> new PublicModelNotFoundException(id));
        model.update(basePriceRatio, usePrice, basePrice, enabled, displayName, sortOrder, description);
        PublicModel saved = repository.save(model);
        refreshPricing.refresh();
        return saved;
    }

    /**
     * 软删除对外模型（F-6001）。
     *
     * @param id 对外模型 id
     * @throws PublicModelNotFoundException 不存在
     */
    @Transactional
    public void delete(long id) {
        if (repository.findById(id).isEmpty()) {
            throw new PublicModelNotFoundException(id);
        }
        repository.deleteById(id);
        refreshPricing.refresh();
    }
}
