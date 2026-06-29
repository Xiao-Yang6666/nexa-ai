package com.nexa.application.billing;

import com.nexa.application.billing.port.UserQuotaAccount;
import com.nexa.domain.billing.exception.InvalidBillingParameterException;
import com.nexa.domain.billing.model.BalanceTransaction;
import com.nexa.domain.billing.repository.BalanceTransactionRepository;
import com.nexa.domain.billing.vo.BalanceTransactionType;
import com.nexa.domain.billing.vo.Quota;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理员余额调整用例（后台给指定用户充值/扣费 + 账变留痕）。
 *
 * <p>充值：{@code users.quota += amount} 并记 {@link BalanceTransactionType#ADMIN_CREDIT}。
 * 扣费：<b>扣到 0 为止</b>（不允许欠费），按实扣额记 {@link BalanceTransactionType#ADMIN_DEBIT}。
 * 入账/扣减与写账变在<b>同一事务</b>内完成（原子）。金额单位为 quota（USD↔quota 换算在接口层）。</p>
 */
@Service
public class AdminAdjustBalanceUseCase {

    private final UserQuotaAccount userQuotaAccount;
    private final BalanceTransactionRepository txRepository;

    /**
     * @param userQuotaAccount 用户额度账户端口（原子入账/扣减）
     * @param txRepository     账变流水仓储
     */
    public AdminAdjustBalanceUseCase(UserQuotaAccount userQuotaAccount,
                                     BalanceTransactionRepository txRepository) {
        this.userQuotaAccount = userQuotaAccount;
        this.txRepository = txRepository;
    }

    /**
     * 管理员充值（加额度）。
     *
     * @param userId     目标用户 id（&gt; 0）
     * @param amount     充值额度（quota，&gt; 0）
     * @param operatorId 执行管理员 id
     * @param remark     备注（可空）
     * @return 充值后余额快照（quota）
     * @throws InvalidBillingParameterException 金额非正 / 用户不存在
     */
    @Transactional
    public Quota credit(long userId, long amount, long operatorId, String remark) {
        if (amount <= 0) {
            throw new InvalidBillingParameterException("充值金额必须为正");
        }
        Quota delta = Quota.of(amount);
        userQuotaAccount.credit(userId, delta);
        long after = userQuotaAccount.balanceOf(userId).value();
        txRepository.save(BalanceTransaction.create(
                userId, BalanceTransactionType.ADMIN_CREDIT, amount, after, operatorId, remark));
        return Quota.of(after);
    }

    /**
     * 管理员扣费（扣到 0 为止）。
     *
     * @param userId     目标用户 id（&gt; 0）
     * @param amount     期望扣减额度（quota，&gt; 0）
     * @param operatorId 执行管理员 id
     * @param remark     备注（可空）
     * @return 扣费后余额快照（quota）
     * @throws InvalidBillingParameterException 金额非正 / 用户不存在
     */
    @Transactional
    public Quota debit(long userId, long amount, long operatorId, String remark) {
        if (amount <= 0) {
            throw new InvalidBillingParameterException("扣费金额必须为正");
        }
        // 原子扣到 0，拿到实际扣减额（余额不足时 < amount）。
        Quota actual = userQuotaAccount.debitToZero(userId, Quota.of(amount));
        long after = userQuotaAccount.balanceOf(userId).value();
        if (actual.value() > 0) {
            // 实扣为负数记账（扣费 amount 取负）；实扣为 0（原余额即 0）则不产生账变。
            txRepository.save(BalanceTransaction.create(
                    userId, BalanceTransactionType.ADMIN_DEBIT, -actual.value(), after, operatorId, remark));
        }
        return Quota.of(after);
    }

    /**
     * 查询某用户账变流水（时间倒序）。
     *
     * @param userId 用户 id
     * @param limit  返回上限
     * @return 账变流水
     */
    @Transactional(readOnly = true)
    public List<BalanceTransaction> logs(long userId, int limit) {
        return txRepository.findByUser(userId, limit);
    }
}
