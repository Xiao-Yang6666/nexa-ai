package com.nexa.application.billing;

import com.nexa.domain.billing.model.Redemption;
import com.nexa.domain.billing.repository.RedemptionRepository;
import com.nexa.domain.billing.vo.Quota;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.nexa.application.billing.command.GenerateRedemptionsCommand;

/**
 * 兑换码生成用例（管理端单个/批量，prd-billing BL-4 §5 生成侧，F-2045）。
 *
 * <p>应用层用例：按 {@code count} 循环调用聚合工厂 {@link Redemption#create} 生成 N 张未使用码，
 * 批量持久化后返回明文集合（openapi {@code POST /api/redemption/} 返回 string[] 明文）。
 * {@code count} 上界由用例守护，避免一次生成过多打穿 DB。</p>
 */
@Service
public class GenerateRedemptionsUseCase {

    /** 单次批量生成上限（防御：避免误传巨量 count 打穿 DB / 内存）。 */
    private static final int MAX_BATCH = 1000;

    private final RedemptionRepository redemptionRepository;

    /**
     * @param redemptionRepository 兑换码仓储
     */
    public GenerateRedemptionsUseCase(RedemptionRepository redemptionRepository) {
        this.redemptionRepository = redemptionRepository;
    }

    /**
     * 生成兑换码（单个或批量），返回明文 Key 集合。
     *
     * @param creatorUserId 创建者用户 id（认证主体注入）
     * @param command       生成命令（名称/面额/数量/过期时间）
     * @return 生成的兑换码明文列表（按生成顺序）
     */
    @Transactional
    public List<String> generate(int creatorUserId, GenerateRedemptionsCommand command) {
        // count 缺省/非正回落 1（单个生成）；上界裁剪到 MAX_BATCH。
        int count = command.count() == null || command.count() <= 0 ? 1 : command.count();
        count = Math.min(count, MAX_BATCH);

        Quota quota = Quota.of(command.quota() == null ? 100L : command.quota());
        long now = Instant.now().getEpochSecond();

        List<Redemption> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            batch.add(Redemption.create(creatorUserId, command.name(), quota, command.expiredTime(), now));
        }
        List<Redemption> saved = redemptionRepository.saveAll(batch);
        return saved.stream().map(Redemption::key).toList();
    }
}
