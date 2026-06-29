package com.nexa.domain.relay.port;

import java.util.Optional;
import java.util.Set;

/**
 * 账号选择端口（relay 域定义，account.provider BC 实现——DDD 依赖倒置）。
 *
 * <p>转发链选中 channel(路由/映射/定价策略层)后，再从同一字符串 group 的 account 池(凭证资源池)
 * 选一个可调度账号取凭证。account 与 channel 在 group 层汇合(原汁 sub2api)：channel 决定"映射成
 * 哪个 B、按什么定价"，account 决定"用谁的凭证、发往哪个上游"。</p>
 *
 * <p>选择规则(由 adapter 实现)：按 group 命中 account 池 → status=ACTIVE+未限流+未过期+未过载
 * (领域 isSchedulable 终判) → 按账号 priority 升序(小=高优先)取首个未排除者。platform 软对齐：
 * 优先选与 channel 平台匹配的账号(B 要发往该平台上游)，无匹配再放宽。</p>
 */
public interface AccountSelectionPort {

    /**
     * 选一个可服务给定模型 A 的可调度账号取凭证（方案乙：按模型反查，售价分组与调度解耦）。
     *
     * @param requestedModel    客户请求并解析出的平台模型名 A（账号须声明支持该模型才入选）
     * @param platform          期望平台（目标上游平台；null/空=不约束平台）
     * @param excludeAccountIds 本次已尝试、需排除的账号 id 集合（重试用，可空）
     * @return 选中的账号凭证投影；无可用账号返回 {@link Optional#empty()}
     */
    Optional<SelectedAccount> selectAccount(String requestedModel, String platform, Set<Long> excludeAccountIds);

    /**
     * 标记账号进入限流(上游 429 触发，转发失败回写)。account BC 持久化状态迁移。
     *
     * @param accountId 账号 id
     * @param resetAt   限流恢复时刻 epoch 秒(可空)
     */
    void markRateLimited(long accountId, Long resetAt);

    /**
     * 标记账号进入过载冷却(上游 529 触发，转发失败回写)。account BC 持久化状态迁移。
     *
     * @param accountId 账号 id
     * @param until     过载冷却截止时刻 epoch 秒
     */
    void markOverloaded(long accountId, Long until);
}
