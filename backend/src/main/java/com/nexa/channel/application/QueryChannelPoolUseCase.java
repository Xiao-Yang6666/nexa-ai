package com.nexa.channel.application;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 供应渠道池查询用例（应用层，F-6005）。
 *
 * <p>管理端端点：按对外模型 A / 真实模型 B / 分组查供应渠道池（AdminView，含 B，客户绝不可见）。
 * 渠道池本体复用 Channel/Ability 路由；本用例仅做查询型投影——列出同一 B 下的渠道成员（同品质红线
 * ADR-BILL-05：混渠道前品质已拆分为独立 A，此处查询不再校验品质一致）。</p>
 *
 * <p>F-6005 背景：COMPAT-BILLING §3「模型分级」+ API-ENDPOINTS §5.6「渠道池本体复用 Ability/Channel 路由；
 * 选渠为系统内部行为；管理侧通过给同一真实模型 B 挂多渠道实现」。本查询端点让管理员可视化同一 B 下的多供应商池成员。</p>
 */
@Service
public class QueryChannelPoolUseCase {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public QueryChannelPoolUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 查供应渠道池（F-6005，按 group + upstream_model=B 过滤）。
     *
     * <p>逻辑：遍历全部渠道，过滤 {@code group} 匹配（可空 → 不过滤）+
     * {@code models} CSV 包含 {@code upstreamModel}（可空 → 不过滤）。
     * 结果 DTO 投影返回 channel_id/channel_name/upstream_model/group/priority/weight/status/enabled。</p>
     *
     * @param group         分组过滤（可空 → 不过滤）
     * @param upstreamModel 真实模型 B 过滤（可空 → 不过滤，返回该 group 全部渠道成员）
     * @return 渠道池成员列表（按 priority 升序、id 升序；AdminView，含 B，客户绝不可见）
     */
    @Transactional(readOnly = true)
    public List<Channel> queryPool(String group, String upstreamModel) {
        List<Channel> all = channelRepository.findAll();
        return all.stream()
                .filter(c -> matchGroup(c, group))
                .filter(c -> matchModel(c, upstreamModel))
                .sorted((a, b) -> {
                    int cmp = Long.compare(a.priority(), b.priority());
                    return cmp != 0 ? cmp : Long.compare(a.id(), b.id());
                })
                .toList();
    }

    /**
     * 分组匹配（可空 group 不过滤）。
     *
     * @param channel 渠道
     * @param group   目标分组（可空）
     * @return group 为空 → true；否则等值匹配
     */
    private boolean matchGroup(Channel channel, String group) {
        if (group == null || group.isBlank()) {
            return true;
        }
        return group.trim().equalsIgnoreCase(channel.group());
    }

    /**
     * 模型匹配（models 是逗号分隔 CSV，B 为空不过滤）。
     *
     * @param channel       渠道（models 字段逗号分隔）
     * @param upstreamModel 真实模型 B（可空）
     * @return B 为空 → true；否则 models CSV 包含 B（大小写不敏感、去空白）
     */
    private boolean matchModel(Channel channel, String upstreamModel) {
        if (upstreamModel == null || upstreamModel.isBlank()) {
            return true;
        }
        String b = upstreamModel.trim().toLowerCase(java.util.Locale.ROOT);
        String models = channel.models();
        if (models == null || models.isEmpty()) {
            return false;
        }
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(m -> m.equals(b));
    }
}
