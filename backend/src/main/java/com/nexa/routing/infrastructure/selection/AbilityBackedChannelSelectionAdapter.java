package com.nexa.routing.infrastructure.selection;

import com.nexa.channel.infrastructure.persistence.SpringDataAbilityJpaRepository;
import com.nexa.channel.infrastructure.persistence.entity.AbilityJpaEntity;
import com.nexa.routing.application.port.ChannelSelectionPort;
import com.nexa.routing.domain.vo.ChannelCandidate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CH-2 选渠委托端口的真实实现（基础设施层 adapter，V25 Ability 表驱动）。
 *
 * <p>替换原 {@code StubChannelSelectionAdapter}。按 (group, model, enabled=true) 从 Ability 表
 * 拉候选，按 {@code priority} 降序分桶（层），按 {@code priorityRetry} 降若干层后对该层做 Weight 加权随机
 * 抽签，返回选中渠道 {@link ChannelCandidate}。算法对齐 FC-072 {@code GetRandomSatisfiedChannel}：</p>
 * <ol>
 *   <li>筛 enabled=true 候选；按 priority 降序分组层。</li>
 *   <li>priorityRetry=0 → 取最高优先级层；priorityRetry=1 → 降一层，依此类推。</li>
 *   <li>层内按 weight 加权随机（weight≤0 视为 0，不进入抽签池）。层内全部 weight=0 时随机等概率取一。</li>
 *   <li>该层无候选或 priorityRetry 超出现有层数 → 返回 null（调用方 CH-5 判定「组耗尽」切组/降级）。</li>
 * </ol>
 *
 * <p>实现说明：每次选渠走一次 SQL + 内存分桶抽签；量级为单 (group, model) 的候选数（通常个位数到数十），
 * 无额外缓存，简单正确优先。后续如需性能可在 Ability 写入时内存化，非本期必做。</p>
 */
@Component
public class AbilityBackedChannelSelectionAdapter implements ChannelSelectionPort {

    private final SpringDataAbilityJpaRepository abilityRepository;

    /**
     * @param abilityRepository Ability 路由索引仓储
     */
    public AbilityBackedChannelSelectionAdapter(SpringDataAbilityJpaRepository abilityRepository) {
        this.abilityRepository = abilityRepository;
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCandidate selectChannel(String group, String model, int priorityRetry) {
        if (group == null || model == null) return null;
        List<AbilityJpaEntity> satisfied = abilityRepository.findSatisfied(group, model);
        if (satisfied.isEmpty()) return null;

        // 按 priority 降序分桶（TreeMap 反序 = 高优先级在前）。
        TreeMap<Long, List<AbilityJpaEntity>> tiers = new TreeMap<>(Comparator.reverseOrder());
        for (AbilityJpaEntity a : satisfied) {
            tiers.computeIfAbsent(a.getPriority(), k -> new java.util.ArrayList<>()).add(a);
        }

        // 跳到 priorityRetry 指定层（0 = 最高优先级层）。
        int skip = Math.max(0, priorityRetry);
        var iter = tiers.entrySet().iterator();
        Map.Entry<Long, List<AbilityJpaEntity>> chosen = null;
        for (int i = 0; i <= skip && iter.hasNext(); i++) {
            chosen = iter.next();
        }
        if (chosen == null || skip >= tiers.size()) {
            return null;
        }
        List<AbilityJpaEntity> layer = chosen.getValue();
        AbilityJpaEntity picked = weightedPick(layer, ThreadLocalRandom.current());
        if (picked == null) return null;
        return new ChannelCandidate(picked.getChannelId(), group, picked.getPriority(), picked.getWeight());
    }

    /**
     * 层内按 weight 加权随机（weight≤0 视为权重 0 不参与抽签）。
     * 若全部 weight=0，则等概率取一（避免新渠道未配置权重时完全选不到）。
     *
     * @param layer 当前优先级层候选集
     * @param rnd   随机源
     * @return 选中项；层空返回 null
     */
    private AbilityJpaEntity weightedPick(List<AbilityJpaEntity> layer, Random rnd) {
        if (layer.isEmpty()) return null;
        // 第一遍：累计总权重（weight>0 才计）。
        long total = 0;
        for (AbilityJpaEntity a : layer) {
            if (a.getWeight() > 0) total += a.getWeight();
        }
        if (total <= 0) {
            // 全 0 → 等概率。
            return layer.get(rnd.nextInt(layer.size()));
        }
        long hit = (long) (rnd.nextDouble() * total);
        long acc = 0;
        for (AbilityJpaEntity a : layer) {
            if (a.getWeight() > 0) {
                acc += a.getWeight();
                if (hit < acc) return a;
            }
        }
        // 浮点误差兜底，返回最后一个有权重的项。
        for (int i = layer.size() - 1; i >= 0; i--) {
            if (layer.get(i).getWeight() > 0) return layer.get(i);
        }
        return layer.get(layer.size() - 1);
    }
}
