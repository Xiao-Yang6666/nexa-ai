package com.nexa.billing.domain.model;

import com.nexa.billing.domain.exception.InvalidBillingParameterException;
import com.nexa.billing.domain.vo.BalanceTransactionType;

import java.time.Instant;

/**
 * 账变流水领域模型（用户余额账变记录，管理操作审计 + 充值到账留痕）。
 *
 * <p>一条记录 = 一次余额变动。{@code amount} 带正负号（充值为正、扣费为负，quota 单位），
 * {@code balanceAfter} 为变动后余额快照（便于对账与展示，无需回放）。{@code operatorId} 为执行
 * 管理员 id（自助/兑换到账类可空）。本模型只承载「管理操作 + 充值到账」类账变，不含 API 消费扣减。</p>
 *
 * <p>DDD：domain 零框架依赖（纯 Java，可单测）。不可变记录语义——账变一旦发生即为历史事实，
 * 无修改行为，仅 create（新发生）+ rehydrate（持久化重建）。</p>
 */
public final class BalanceTransaction {

    private Long id;
    private final long userId;
    private final BalanceTransactionType type;
    private final long amount;        // 带正负：充值>0，扣费<0
    private final long balanceAfter;  // 变动后余额快照（quota）
    private final Long operatorId;    // 执行管理员 id（自助/兑换可空）
    private final String remark;
    private final long createdTime;

    private BalanceTransaction(Long id, long userId, BalanceTransactionType type, long amount,
                               long balanceAfter, Long operatorId, String remark, long createdTime) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.operatorId = operatorId;
        this.remark = remark;
        this.createdTime = createdTime;
    }

    /**
     * 新建一条账变记录（未持久化，id 为 null）。
     *
     * @param userId       目标用户 id（&gt; 0）
     * @param type         账变类型（非空）
     * @param amount       变动额（带正负，quota 单位；不为 0）
     * @param balanceAfter 变动后余额（quota，&gt;= 0）
     * @param operatorId   执行管理员 id（可空）
     * @param remark       备注（可空）
     * @return 新账变记录
     * @throws InvalidBillingParameterException 参数非法
     */
    public static BalanceTransaction create(long userId, BalanceTransactionType type, long amount,
                                            long balanceAfter, Long operatorId, String remark) {
        if (userId <= 0) {
            throw new InvalidBillingParameterException("balance transaction userId must be positive");
        }
        if (type == null) {
            throw new InvalidBillingParameterException("balance transaction type is required");
        }
        if (amount == 0) {
            throw new InvalidBillingParameterException("balance transaction amount must be non-zero");
        }
        return new BalanceTransaction(null, userId, type, amount, balanceAfter, operatorId,
                normalizeRemark(remark), Instant.now().getEpochSecond());
    }

    /**
     * 持久化重建（基础设施层用，绕过校验，信任已落库数据）。
     *
     * @param id           主键
     * @param userId       用户 id
     * @param type         类型
     * @param amount       变动额（带正负）
     * @param balanceAfter 变动后余额
     * @param operatorId   执行管理员 id（可空）
     * @param remark       备注（可空）
     * @param createdTime  创建时间 epoch 秒
     * @return 重建的账变记录
     */
    public static BalanceTransaction rehydrate(Long id, long userId, BalanceTransactionType type, long amount,
                                               long balanceAfter, Long operatorId, String remark, long createdTime) {
        return new BalanceTransaction(id, userId, type, amount, balanceAfter, operatorId, remark, createdTime);
    }

    /** 持久化后回填自增 id。 @param assignedId 自增主键 */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    private static String normalizeRemark(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ---- 只读访问器 ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 用户 id */
    public long userId() {
        return userId;
    }

    /** @return 账变类型 */
    public BalanceTransactionType type() {
        return type;
    }

    /** @return 变动额（带正负，quota） */
    public long amount() {
        return amount;
    }

    /** @return 变动后余额快照（quota） */
    public long balanceAfter() {
        return balanceAfter;
    }

    /** @return 执行管理员 id（可空） */
    public Long operatorId() {
        return operatorId;
    }

    /** @return 备注（可空） */
    public String remark() {
        return remark;
    }

    /** @return 创建时间 epoch 秒 */
    public long createdTime() {
        return createdTime;
    }
}
