package com.nexa.channel.application;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 批量操作渠道用例（应用层，F-2016 POST /api/channel/batch）。
 *
 * <p>用例编排（事务边界）：对一组渠道 id 执行启用/禁用/删除。openapi 标「幂等键 (ids, action)」——
 * 同 (ids, action) 多次执行结果一致。支持的 action：
 * <ul>
 *   <li>{@code enable}  → 批量置 Status=ENABLED。</li>
 *   <li>{@code disable} → 批量置 Status=MANUALLY_DISABLED。</li>
 *   <li>{@code delete}  → 批量物理删除。</li>
 * </ul>
 * 未知 action / 空 ids 抛 {@link InvalidChannelParameterException}（→400）。返回受影响渠道数。</p>
 */
@Service
public class BatchOperateChannelsUseCase {

    /** 批量启用 action。 */
    public static final String ACTION_ENABLE = "enable";

    /** 批量禁用 action。 */
    public static final String ACTION_DISABLE = "disable";

    /** 批量删除 action。 */
    public static final String ACTION_DELETE = "delete";

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道仓储 */
    public BatchOperateChannelsUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * 批量操作渠道。
     *
     * @param ids    渠道 id 集合（非空）
     * @param action 操作类型（enable/disable/delete）
     * @return 受影响渠道数
     * @throws InvalidChannelParameterException ids 为空或 action 非法
     */
    @Transactional
    public int operate(List<Long> ids, String action) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidChannelParameterException("ids must not be empty");
        }
        String act = action == null ? "" : action.trim().toLowerCase();
        return switch (act) {
            case ACTION_ENABLE -> channelRepository.updateStatusByIds(ids, ChannelStatus.ENABLED);
            case ACTION_DISABLE -> channelRepository.updateStatusByIds(ids, ChannelStatus.MANUALLY_DISABLED);
            case ACTION_DELETE -> {
                int affected = 0;
                // 仅删除实际存在的渠道，受影响数为真实命中数（幂等：重复删已删 id 不计数、不报错）。
                for (Long id : channelRepository.findByIds(ids).stream().map(c -> c.id()).toList()) {
                    channelRepository.deleteById(id);
                    affected++;
                }
                yield affected;
            }
            default -> throw new InvalidChannelParameterException("unsupported batch action: " + action);
        };
    }
}
