package com.nexa.log.domain.vo;

/**
 * 用量排行榜条目值对象（F-4010 用量排行快照，公开口径）。
 *
 * <p>领域规则来源：prd 日志与用量 F-4010 + openapi RankingPublicView「绝不含成本/利润/上游模型 B/供应商」。
 * 排行<b>只按对外公开名 A</b>（{@code resolved_public_model}）聚合消费量——这是可见性铁律在排行场景的落地：
 * 排行对匿名/普通用户公开，必须用 A 而非 B/渠道（杜绝从排行反推上游供应商）。{@code rank} 由聚合后
 * 按量降序生成（1 起）。</p>
 *
 * @param rank           名次（1 起，按用量降序）
 * @param publicModel    对外公开名 A（resolved_public_model）
 * @param usedQuota      聚合用量（quota 总和；公开口径用售价 quota，绝不暴露成本/利润）
 * @param period         统计周期（week/month）
 * @param snapshotTime   快照时间（epoch 秒，本次实时聚合时刻）
 */
public record RankingEntry(int rank, String publicModel, long usedQuota, String period, long snapshotTime) {
}
