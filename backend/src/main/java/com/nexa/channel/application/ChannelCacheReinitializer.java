package com.nexa.channel.application;

/**
 * 渠道缓存重建端口（应用层 / 防腐层接口，F-4045 副作用 InitChannelCache）。
 *
 * <p>DDD 铁律：应用层只依赖本端口、不依赖具体缓存实现（Redis/本地缓存/选渠路由内存表等，
 * backend-engineer §2.3）。基础设施层实现本接口，封装「重建指定渠道的路由缓存」这一基础设施关注点。</p>
 *
 * <p>语义来源：API-ENDPOINTS §5.8 / BACKLOG F-4045——Codex 渠道凭证自动刷新回写后，对
 * status∈{1,3}（启用/自动禁用）的渠道触发 InitChannelCache，使选渠路由的缓存凭证与库中新 key 一致。
 * 调用方（{@link QueryCodexUsageUseCase}）已判定 {@code shouldReinitCacheAfterKeyRefresh()} 后才调本端口。</p>
 */
public interface ChannelCacheReinitializer {

    /**
     * 重建指定渠道的路由缓存（InitChannelCache）。
     *
     * <p>实现应幂等（重复重建同一渠道无副作用差异）；重建失败不应影响已成功的用量查询主流程
     * （由实现决定吞掉记录日志还是抛出——本片 stub 为空操作）。</p>
     *
     * @param channelId 渠道 id（非空）
     */
    void reinitialize(Long channelId);
}
