package com.nexa.domain.routing.vo;

/**
 * 满足渠道候选值对象（不可变，选渠引擎输出的单个渠道快照，CH-2/CH-5/CH-6 共用）。
 *
 * <p>领域规则：选渠引擎从 Ability 表筛出满足 (group, model, enabled=true) 的渠道后按
 * Priority/Weight 抽签选定一个——本 VO 是最终选定结果的轻量快照，仅携带选渠后续所需字段
 * （channel_id/group/priority/weight），不是完整 Channel 聚合（避免选渠路径拖整个聚合入内存）。</p>
 *
 * @param channelId 渠道 id
 * @param group     分组
 * @param priority  优先级层级
 * @param weight    权重
 */
public record ChannelCandidate(long channelId, String group, long priority, int weight) {
}
