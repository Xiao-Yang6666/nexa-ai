package com.nexa.domain.nfr.service;

/**
 * 渠道健康度与故障切换领域服务（F-5003 渠道健康度看板，NFR-A02/A04，纯领域规则零框架依赖）。
 *
 * <p>定义「故障渠道命中后如何重试切换到健康渠道」的领域规则。领域规则来源：BACKLOG T-207 F-5003 验收
 * 「故障渠道命中后 ≤1 次重试切换健康渠道」、API-ENDPOINTS §14.2 F-5003。把「最多重试几次、何时判定渠道
 * 不健康、切换决策」收敛为可单测的领域服务（backend-engineer §2.4），供选渠/转发链路（relay/routing）引用，
 * 健康度数据源为 perf_metrics + channel-test，展示复用 §9.3 与模块五渠道测试，本 BC 不挂端点。</p>
 */
public final class ChannelHealthPolicy {

    /**
     * 单次请求允许的最大重试切换次数（F-5003 验收「≤1 次重试」）。
     *
     * <p>语义：首选渠道命中故障后，最多再切换 1 次到健康渠道——即一次请求最多触达 2 个渠道
     * （首选 + 1 个备选）。超过即放弃并返回错误，避免重试风暴放大故障（NFR-A04）。</p>
     */
    public static final int MAX_FAILOVER_RETRIES = 1;

    /**
     * 判定渠道「不健康」的连续失败阈值。
     *
     * <p>连续失败达到该阈值即视为不健康，应从候选中临时摘除（熔断），由健康探测恢复后再纳入。
     * 取 3：偶发抖动（1~2 次）不误判，持续失败才摘除（NFR-A02 健康度判定）。</p>
     */
    public static final int UNHEALTHY_CONSECUTIVE_FAILURES = 3;

    private ChannelHealthPolicy() {
    }

    /**
     * 判断在已重试 {@code retriesSoFar} 次后是否还允许再切换一次渠道。
     *
     * <p>领域规则：累计重试切换次数 &lt; {@link #MAX_FAILOVER_RETRIES} 时才允许再切换。
     * 选渠/转发链路命中故障渠道后调用本方法决定是否再挑一个健康渠道重试。</p>
     *
     * @param retriesSoFar 本请求已发生的重试切换次数（&gt;=0）
     * @return 允许再切换返回 {@code true}
     */
    public static boolean canFailover(int retriesSoFar) {
        return retriesSoFar < MAX_FAILOVER_RETRIES;
    }

    /**
     * 根据连续失败次数判定渠道是否应被视为不健康（熔断摘除）。
     *
     * @param consecutiveFailures 该渠道当前连续失败次数（&gt;=0）
     * @return 不健康（应摘除）返回 {@code true}
     */
    public static boolean isUnhealthy(int consecutiveFailures) {
        return consecutiveFailures >= UNHEALTHY_CONSECUTIVE_FAILURES;
    }

    /**
     * 给定首选渠道是否故障 + 已重试次数，判定本次是否应执行「切换到健康渠道」。
     *
     * <p>领域规则：首选渠道故障（{@code primaryFailed=true}）且仍在重试预算内
     * （{@link #canFailover}）→ 应切换；否则不切换（首选正常，或已用尽重试预算）。</p>
     *
     * @param primaryFailed 首选/当前渠道是否命中故障
     * @param retriesSoFar  已重试切换次数
     * @return 应切换返回 {@code true}
     */
    public static boolean shouldSwitchToHealthy(boolean primaryFailed, int retriesSoFar) {
        return primaryFailed && canFailover(retriesSoFar);
    }
}
