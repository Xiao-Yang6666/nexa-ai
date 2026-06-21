package com.nexa.channel.application;

import com.nexa.channel.domain.exception.ChannelModelCostNotFoundException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.model.ChannelModelCost;
import com.nexa.channel.domain.repository.ChannelModelCostRepository;
import com.nexa.channel.domain.vo.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 供应商成本配置 CRUD 用例（应用层，F-6006）。
 *
 * <p>本用例 <b>仅</b> admin/root 调用（接口层守门）——客户无任何读路径（B 不可见三道闸之应用层闸）。
 * 核心语义：upsert——同 (channel_id, upstream_model) 已有行则更新，无则 insert（DB-SCHEMA §19
 * uk_channel_model）；超管手填成本倍率（COMPAT §4「成本倍率录入方式：A 起步——超管后台手动填」）。</p>
 */
@Service
public class ManageChannelModelCostUseCase {

    private final ChannelModelCostRepository repository;

    /** @param repository 成本配置仓储 */
    public ManageChannelModelCostUseCase(ChannelModelCostRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页列表（F-6006，可选 channel_id / upstream_model 过滤）。
     *
     * @param channelId     渠道 id 过滤（可空）
     * @param upstreamModel B 过滤（可空）
     * @param pagination    分页参数
     * @return 当前页成本列表
     */
    @Transactional(readOnly = true)
    public List<ChannelModelCost> list(Integer channelId, String upstreamModel, Pagination pagination) {
        return repository.findPage(channelId, upstreamModel, pagination);
    }

    /**
     * 总条数（F-6006 列表 total）。
     *
     * @param channelId     渠道 id 过滤（可空）
     * @param upstreamModel B 过滤（可空）
     * @return 满足条件的总数
     */
    @Transactional(readOnly = true)
    public long count(Integer channelId, String upstreamModel) {
        return repository.count(channelId, upstreamModel);
    }

    /**
     * 创建或更新成本配置（F-6006 upsert，超管手填）。
     *
     * <p>同 (channel_id, upstream_model) 已有行则覆盖更新，无则新建（uk_channel_model 幂等键）。</p>
     *
     * @param channelId           渠道 id（必填，&gt; 0）
     * @param upstreamModel       真实模型 B（必填）
     * @param costRatio           成本倍率
     * @param completionCostRatio 成本补全倍率（0=回落）
     * @param enabled             是否启用
     * @param sourceUnitPrice     进货单价（扩展位）
     * @param remark              备注
     * @return 落库后的成本行
     * @throws InvalidChannelParameterException 入参非法
     */
    @Transactional
    public ChannelModelCost upsert(Integer channelId, String upstreamModel, BigDecimal costRatio,
                                   BigDecimal completionCostRatio, Boolean enabled,
                                   BigDecimal sourceUnitPrice, String remark) {
        if (channelId == null || channelId <= 0) {
            throw new InvalidChannelParameterException("channel_id 必须为正整数");
        }
        if (upstreamModel == null || upstreamModel.isBlank()) {
            throw new InvalidChannelParameterException("upstream_model 不能为空");
        }
        Optional<ChannelModelCost> existing =
                repository.findByChannelAndUpstream(channelId, upstreamModel.trim());
        if (existing.isPresent()) {
            // 已有行 → 覆盖更新（幂等键不变）。
            ChannelModelCost cost = existing.get();
            cost.update(costRatio, completionCostRatio, enabled, sourceUnitPrice, remark);
            return repository.save(cost);
        }
        ChannelModelCost cost = ChannelModelCost.create(channelId, upstreamModel, costRatio,
                completionCostRatio, enabled, null, sourceUnitPrice, remark);
        return repository.save(cost);
    }

    /**
     * 软删除成本配置（F-6006，删后该渠道×B 成本视为缺失记 0+告警）。
     *
     * @param id 成本行 id
     * @throws ChannelModelCostNotFoundException 不存在
     */
    @Transactional
    public void delete(long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ChannelModelCostNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
