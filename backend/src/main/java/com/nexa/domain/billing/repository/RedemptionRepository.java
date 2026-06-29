package com.nexa.domain.billing.repository;

import com.nexa.domain.billing.model.Redemption;

import java.util.List;
import java.util.Optional;

/**
 * 兑换码聚合仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，单测可桩替换。实现见
 * {@code infrastructure.persistence.RedemptionRepositoryImpl}。</p>
 */
public interface RedemptionRepository {

    /**
     * 按明文 Key 查找兑换码（用户兑换时定位，prd-billing BL-4 rd_find）。
     *
     * @param key 兑换码明文
     * @return 命中返回聚合，否则空（空 → 应用层抛 RedemptionInvalidException）
     */
    Optional<Redemption> findByKey(String key);

    /**
     * 持久化兑换码聚合（新增或更新）。
     *
     * <p>新码保存后回填自增 id（批量生成时不必，但兑换置已用需更新既有行）。</p>
     *
     * @param redemption 待保存的聚合
     * @return 持久化后的聚合（含 id）
     */
    Redemption save(Redemption redemption);

    /**
     * 批量持久化（管理员一次生成 N 张兑换码，prd-billing BL-4 §5 Count）。
     *
     * @param redemptions 待保存的聚合列表
     * @return 持久化后的聚合列表（含 id）
     */
    List<Redemption> saveAll(List<Redemption> redemptions);

    /**
     * 分页查询兑换码（管理端列表，openapi {@code GET /api/redemption/}，按 id 降序）。
     *
     * @param page     页码（从 1 起）
     * @param pageSize 每页条数（&gt; 0）
     * @return 当页兑换码 + 总数的分页结果
     */
    Page<Redemption> findPage(int page, int pageSize);

    /**
     * 管理端分页结果（领域层轻量分页载体）。
     *
     * @param items    当页聚合列表
     * @param total    匹配总条数
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      载荷类型
     */
    record Page<T>(List<T> items, long total, int page, int pageSize) {
    }
}
