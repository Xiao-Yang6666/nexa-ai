package com.nexa.routing.infrastructure.selection;

import com.nexa.account.provider.infrastructure.persistence.SpringDataAccountAbilityJpaRepository;
import com.nexa.account.provider.infrastructure.persistence.entity.AccountAbilityJpaEntity;
import com.nexa.routing.application.port.ChannelSelectionPort;
import com.nexa.routing.domain.vo.ChannelCandidate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * CH-2 选渠委托端口实现（账号维度 Ability 表驱动，R7 整合后）。
 *
 * <p>渠道概念已整合为「供应商账号」，路由索引下沉至账号域 {@code abilities}(account_id × group × models)。
 * 本 adapter 按 (group, model) 从账号 ability 表拉候选账号，返回其 account_id 作为 {@link ChannelCandidate}。
 * 注：relay 转发主链已改为直接走 {@code AccountSelectionPort}，本端口仅为兼容 routing BC 既有契约保留，
 * priority/weight 不再由 ability 表承载（账号选择的优先级在 account 聚合内），此处统一以 0 占位。</p>
 */
@Component
public class AbilityBackedChannelSelectionAdapter implements ChannelSelectionPort {

    private final SpringDataAccountAbilityJpaRepository abilityRepository;

    /**
     * @param abilityRepository 账号 Ability 路由索引仓储
     */
    public AbilityBackedChannelSelectionAdapter(SpringDataAccountAbilityJpaRepository abilityRepository) {
        this.abilityRepository = abilityRepository;
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCandidate selectChannel(String group, String model, int priorityRetry) {
        return selectChannel(group, model, priorityRetry, null);
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCandidate selectChannel(String group, String model, int priorityRetry,
                                          Set<Long> excludeChannelIds) {
        if (group == null || model == null) return null;
        List<AccountAbilityJpaEntity> satisfied = abilityRepository.findActiveByGroup(group).stream()
                .filter(a -> declaresModel(a.getModels(), model))
                .toList();
        if (satisfied.isEmpty()) return null;

        // 重试切换：剔除已尝试账号。
        if (excludeChannelIds != null && !excludeChannelIds.isEmpty()) {
            satisfied = satisfied.stream()
                    .filter(a -> !excludeChannelIds.contains(a.getAccountId()))
                    .toList();
            if (satisfied.isEmpty()) return null;
        }

        // priorityRetry 作为简单下标偏移（账号优先级在 account 聚合内做精细排序，此处简化）。
        int idx = Math.max(0, priorityRetry);
        if (idx >= satisfied.size()) return null;
        AccountAbilityJpaEntity picked = satisfied.get(idx);
        return new ChannelCandidate(picked.getAccountId(), group, 0L, 0);
    }

    /** models 逗号分隔串是否声明支持给定模型。 */
    private boolean declaresModel(String models, String model) {
        if (models == null || models.isBlank()) return false;
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .anyMatch(model::equals);
    }
}
