package com.nexa.channel.domain.repository;

import com.nexa.channel.domain.model.ChannelModelCost;
import com.nexa.channel.domain.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 供应商成本倍率仓储接口（领域层定义，基础设施层实现，F-6006）。
 *
 * <p>DDD 依赖倒置（backend-engineer §2.3）。关联表：V14 {@code channel_model_costs}。
 * <b>客户无任何读路径</b>：本仓储仅 admin/root 应用层调用。</p>
 */
public interface ChannelModelCostRepository {

    /**
     * 保存（新增或更新）成本行。新建保存后返回携带自增 id 的聚合。
     *
     * @param cost 待保存的成本聚合
     * @return 持久化后的成本（新建含 id）
     */
    ChannelModelCost save(ChannelModelCost cost);

    /**
     * 按主键查成本行。
     *
     * @param id 主键
     * @return 命中返回聚合，否则空
     */
    Optional<ChannelModelCost> findById(long id);

    /**
     * 按 (channel_id, upstream_model) 查（uk_channel_model 幂等键）。
     *
     * @param channelId     渠道 id
     * @param upstreamModel 真实模型 B
     * @return 命中返回聚合，否则空
     */
    Optional<ChannelModelCost> findByChannelAndUpstream(int channelId, String upstreamModel);

    /**
     * 按可选条件分页查询（F-6006 列表，channel_id 升序、id 升序）。
     *
     * @param channelId     渠道 id 过滤（可空）
     * @param upstreamModel B 过滤（可空，等值匹配）
     * @param pagination    分页参数
     * @return 当前页成本列表
     */
    List<ChannelModelCost> findPage(Integer channelId, String upstreamModel, Pagination pagination);

    /**
     * 按可选条件统计总数（F-6006 列表 total）。
     *
     * @param channelId     渠道 id 过滤（可空）
     * @param upstreamModel B 过滤（可空）
     * @return 满足条件的总数
     */
    long count(Integer channelId, String upstreamModel);

    /**
     * 按 channel_id + upstream_model 集合批量加载（供应渠道池 enrich 用，避免 N+1）。
     *
     * @param channelIds     渠道 id 集合
     * @param upstreamModels B 集合（与 channelIds 不必同长，按笛卡尔覆盖；空集 → 不过滤 B 维）
     * @return 命中的成本列表
     */
    List<ChannelModelCost> findByChannelsAndUpstreams(List<Integer> channelIds, List<String> upstreamModels);

    /**
     * 软删除成本行（F-6006）。
     *
     * @param id 主键
     */
    void deleteById(long id);
}
