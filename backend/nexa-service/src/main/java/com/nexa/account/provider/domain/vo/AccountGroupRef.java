package com.nexa.account.provider.domain.vo;

/**
 * 账号-分组关联引用值对象（Account 聚合内的关联集合元素，对齐 account_groups 表）。
 *
 * <p>表达「某账号归属某分组及其组内优先级」。作为 Account 聚合的一致性边界内成员，
 * 由仓储在 save 时按 account_id fan-out 到 {@code account_groups}（仿 channel→abilities），
 * findById/findPage 回读组装。{@code groupId} 必填，{@code priority} 为组内优先级（缺省 50）。</p>
 *
 * @param groupId  分组 id（非空）
 * @param priority 组内优先级（>=0）
 */
public record AccountGroupRef(long groupId, int priority) {

    /** 组内默认优先级（对齐 account_groups.priority default 50）。 */
    public static final int DEFAULT_PRIORITY = 50;

    /**
     * 由原始入参归一构造（priority 缺省/负 → 50）。
     *
     * @param groupId  分组 id
     * @param priority 组内优先级（可空/负→50）
     * @return 关联引用值对象
     */
    public static AccountGroupRef of(long groupId, Integer priority) {
        int p = (priority == null || priority < 0) ? DEFAULT_PRIORITY : priority;
        return new AccountGroupRef(groupId, p);
    }
}
