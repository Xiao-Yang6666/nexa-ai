package com.nexa.domain.model.repository;

import com.nexa.domain.model.model.PublicModel;
import com.nexa.domain.model.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 对外模型商品目录仓储接口（领域层定义，基础设施层实现，F-6001/F-6004）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力（backend-engineer §2.3）。实现见
 * {@code infrastructure.persistence.PublicModelRepositoryImpl}。关联表：V11 {@code public_models}。</p>
 *
 * <p>F-6004 模型权限全开：{@link #findEnabledNames()} 返回上架全集，候选层 B 不可见闸的来源。</p>
 */
public interface PublicModelRepository {

    /**
     * 保存（新增或更新）对外模型。新建保存后返回携带自增 id 的聚合。
     *
     * @param model 待保存的对外模型聚合
     * @return 持久化后的对外模型（新建含 id）
     */
    PublicModel save(PublicModel model);

    /**
     * 按主键查对外模型。
     *
     * @param id 主键
     * @return 命中返回聚合，否则空
     */
    Optional<PublicModel> findById(long id);

    /**
     * 按对外名 A 查对外模型（幂等键查重，F-6001）。
     *
     * @param publicName 对外名 A
     * @return 命中返回聚合，否则空
     */
    Optional<PublicModel> findByPublicName(String publicName);

    /**
     * 分页列表（F-6001，按 sort_order 升序、id 升序）。
     *
     * @param pagination 分页参数
     * @param enabledOnly true 仅返回 enabled=true 的；false 全部
     * @return 当前页对外模型列表
     */
    List<PublicModel> findPage(Pagination pagination, boolean enabledOnly);

    /**
     * 总条数（F-6001 列表 total）。
     *
     * @param enabledOnly true 仅统计 enabled=true 的；false 全部
     * @return 满足条件的总数
     */
    long count(boolean enabledOnly);

    /**
     * 列出所有上架对外模型的公开名 A（F-6003 候选层 / F-6004 全员可用判定）。
     *
     * <p>来源：{@code enabled=true AND deleted_at IS NULL} 全集。<b>UserVO 来源</b>，绝不含任何 B
     * （COMPAT §2 候选层 B 不可见闸）。按 sort_order 升序、id 升序。</p>
     *
     * @return 上架公开名 A 列表
     */
    List<String> findEnabledNames();

    /**
     * 列出所有上架对外模型的完整聚合（F-2048 公开价格页定价主体来源）。
     *
     * <p>来源：{@code enabled=true AND deleted_at IS NULL} 全集，按 sort_order 升序、id 升序。
     * 与 {@link #findEnabledNames()} 的区别：返回完整聚合（含售价倍率/品质档/展示名），供公开价格页
     * 逐字段投影成零泄露 PublicView；非分页（价格页一次性渲染全量上架商品）。</p>
     *
     * @return 上架对外模型完整聚合列表
     */
    List<PublicModel> findAllEnabled();

    /**
     * 软删除对外模型（F-6001）。
     *
     * @param id 主键
     */
    void deleteById(long id);
}
