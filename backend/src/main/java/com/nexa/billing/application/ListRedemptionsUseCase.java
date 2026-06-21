package com.nexa.billing.application;

import com.nexa.billing.domain.model.Redemption;
import com.nexa.billing.domain.repository.RedemptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 兑换码列表查询用例（管理端，prd-billing BL-4，F-2045，openapi {@code GET /api/redemption/}）。
 *
 * <p>只读用例：分页返回兑换码聚合，接口层投影为管理视图 {@code RedemptionAdminView}。</p>
 */
@Service
public class ListRedemptionsUseCase {

    private final RedemptionRepository redemptionRepository;

    /**
     * @param redemptionRepository 兑换码仓储
     */
    public ListRedemptionsUseCase(RedemptionRepository redemptionRepository) {
        this.redemptionRepository = redemptionRepository;
    }

    /**
     * 分页查询兑换码。
     *
     * @param page     页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（聚合 + 总数）
     */
    @Transactional(readOnly = true)
    public RedemptionRepository.Page<Redemption> list(int page, int pageSize) {
        return redemptionRepository.findPage(page, pageSize);
    }
}
