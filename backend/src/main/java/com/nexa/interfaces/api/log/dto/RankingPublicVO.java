package com.nexa.interfaces.api.log.dto;

import com.nexa.domain.log.vo.RankingEntry;

/**
 * 用量排行榜公开视图 DTO（接口层，F-4010；**绝不含成本/利润/上游模型 B/供应商**）。
 *
 * <p>对齐 openapi components.schemas.RankingPublicVO：{@code rank / public_model / used_quota_or_count /
 * period / snapshot_time}。可见性铁律在排行场景的序列化层落点——排行对匿名/普通用户公开，只暴露对外
 * 公开名 A（{@code public_model}）与聚合用量，结构上就没有 B/cost/profit/channel 字段。</p>
 *
 * @param rank            名次（1 起）
 * @param publicModel     对外公开名 A
 * @param usedQuotaOrCount 聚合用量（quota 总和）
 * @param period          统计周期（week/month）
 * @param snapshotTime    快照时间（epoch 秒）
 */
public record RankingPublicVO(
        int rank,
        String publicModel,
        long usedQuotaOrCount,
        String period,
        long snapshotTime
) {

    /**
     * 从领域排行条目构造公开视图。
     *
     * @param entry 排行条目值对象
     * @return 公开视图 DTO
     */
    public static RankingPublicVO from(RankingEntry entry) {
        return new RankingPublicVO(
                entry.rank(),
                entry.publicModel(),
                entry.usedQuota(),
                entry.period(),
                entry.snapshotTime());
    }
}
