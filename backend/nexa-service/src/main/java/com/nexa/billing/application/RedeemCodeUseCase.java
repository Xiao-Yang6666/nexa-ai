package com.nexa.billing.application;

import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.billing.domain.exception.RedemptionInvalidException;
import com.nexa.billing.domain.model.BalanceTransaction;
import com.nexa.billing.domain.model.Redemption;
import com.nexa.billing.domain.repository.BalanceTransactionRepository;
import com.nexa.billing.domain.repository.RedemptionRepository;
import com.nexa.billing.domain.vo.BalanceTransactionType;
import com.nexa.billing.domain.vo.Quota;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 兑换码兑换用例（prd-billing BL-4，F-2045）。
 *
 * <p>应用层用例：薄编排 + 事务边界，领域规则在聚合根 {@link Redemption#redeem(long, long)} 内
 * （backend-engineer §2.1 应用层不含领域逻辑）。本用例在<b>同一本地事务</b>内完成「校验 → 入账 →
 * 置已用」，保证原子性（prd-billing BL-4 §2「校验、入账、置已用在同一本地事务内完成」），
 * 杜绝并发重复兑换（AC「同一有效码并发提交两次仅一次成功」）。</p>
 */
@Service
public class RedeemCodeUseCase {

    private final RedemptionRepository redemptionRepository;
    private final UserQuotaAccount userQuotaAccount;
    private final BalanceTransactionRepository txRepository;

    /**
     * @param redemptionRepository 兑换码仓储
     * @param userQuotaAccount     用户额度账户端口（入账，跨域防腐端口）
     * @param txRepository         账变流水仓储（兑换到账留痕）
     */
    public RedeemCodeUseCase(RedemptionRepository redemptionRepository, UserQuotaAccount userQuotaAccount,
                             BalanceTransactionRepository txRepository) {
        this.redemptionRepository = redemptionRepository;
        this.userQuotaAccount = userQuotaAccount;
        this.txRepository = txRepository;
    }

    /**
     * 执行兑换：定位码 → 聚合内校验+置已用 → 入账用户余额（同事务）。
     *
     * <p>码不存在/空 Key → {@link RedemptionInvalidException}（BL-4 rd_find-否，→400）；已用/过期由
     * 聚合 {@code redeem} 抛对应领域异常（→400）。校验通过后先存盘置已用（UNUSED→USED 的更新若
     * 并发已被改则后续行不再 UNUSED，配合事务保证仅一次入账），再给用户加额度。</p>
     *
     * @param redeemerUserId 兑换人用户 id（认证主体注入）
     * @param key            兑换码明文（用户提交）
     * @return 实际入账的面额额度（回执展示）
     * @throws RedemptionInvalidException 码不存在或 Key 空白
     */
    @Transactional
    public Quota redeem(long redeemerUserId, String key) {
        if (key == null || key.isBlank()) {
            throw new RedemptionInvalidException("redemption key must not be blank");
        }
        Redemption redemption = redemptionRepository.findByKey(key.trim())
                .orElseThrow(() -> new RedemptionInvalidException("redemption code not found"));

        long now = Instant.now().getEpochSecond();
        // 聚合内守护一次性/过期不变量并置已用；返回应入账面额。
        Quota amount = redemption.redeem(redeemerUserId, now);
        // 先持久化置已用（同事务），再入账——若并发已置已用，本次更新的行此前已非 UNUSED 而抛已用异常。
        redemptionRepository.save(redemption);
        userQuotaAccount.credit(redeemerUserId, amount);
        // 兑换到账留痕（统一进账变流水，与管理充值/扣费同表，便于按用户展示）。
        long after = userQuotaAccount.balanceOf(redeemerUserId).value();
        txRepository.save(BalanceTransaction.create(
                redeemerUserId, BalanceTransactionType.REDEEM, amount.value(), after, null,
                redemption.name()));
        return amount;
    }
}
