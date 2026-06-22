package com.nexa.growth.application;

import com.nexa.growth.application.port.PaymentComplianceCheck;
import com.nexa.growth.application.port.UserQuotaAccount;
import com.nexa.growth.domain.exception.AffQuotaTransferException;
import com.nexa.growth.domain.exception.GrowthUserNotFoundException;
import com.nexa.growth.domain.model.AffiliateAccount;
import com.nexa.growth.domain.repository.AffiliateAccountRepository;
import com.nexa.growth.infrastructure.config.GrowthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邀请额度划转为可用额度用例（PRD GR-5 «邀请额度划转»，F-1044，{@code POST /api/user/self/aff_transfer}）。
 *
 * <p>带双重前置校验的资金动作（GR-5 T3/T4/T5），在<b>同一本地事务</b>内完成「{@code aff_quota -= quota}
 * + {@code quota += quota}」保证原子（PRD GR-5 §3）。校验职责分层（backend-engineer §2.1/§2.2）：
 * <ul>
 *   <li>合规校验（T5 {@code payment_compliance}）= 应用层前置端口 {@link PaymentComplianceCheck}（外部策略）。</li>
 *   <li>最小单位（T3）+ 邀请额度充足（T4）= 聚合 {@link AffiliateAccount#transferToQuota} 内充血守护。</li>
 * </ul>
 * 并发安全：聚合内逻辑校验 + 仓储 {@code deductAffQuota} 用 CAS 条件 UPDATE 双保险（余额足够才扣，
 * 受影响 0 行 → 视为并发耗尽，抛邀请额度不足）。扣减成功后才给可用余额入账，二者同事务原子提交。</p>
 */
@Service
public class TransferAffQuotaUseCase {

    private final AffiliateAccountRepository affiliateAccountRepository;
    private final UserQuotaAccount userQuotaAccount;
    private final PaymentComplianceCheck paymentComplianceCheck;
    private final GrowthProperties growthProperties;

    /**
     * @param affiliateAccountRepository 邀请返利账户仓储（读 + CAS 扣减）
     * @param userQuotaAccount           用户额度账户端口（同事务入账可用余额）
     * @param paymentComplianceCheck     支付合规校验端口（T5 前置）
     * @param growthProperties           增长常量（提供 QuotaPerUnit 最小划转单位）
     */
    public TransferAffQuotaUseCase(AffiliateAccountRepository affiliateAccountRepository,
                                   UserQuotaAccount userQuotaAccount,
                                   PaymentComplianceCheck paymentComplianceCheck,
                                   GrowthProperties growthProperties) {
        this.affiliateAccountRepository = affiliateAccountRepository;
        this.userQuotaAccount = userQuotaAccount;
        this.paymentComplianceCheck = paymentComplianceCheck;
        this.growthProperties = growthProperties;
    }

    /**
     * 执行邀请额度划转：合规校验 → 聚合内双重校验+扣减 → CAS 落库 → 同事务入账可用余额。
     *
     * <p>对应 GR-5 主流程 T3~T6：① {@code quota < QuotaPerUnit} → 最小额度错（聚合抛）；
     * ② {@code AffQuota < quota} → 邀请额度不足（聚合抛 / CAS 失败抛）；③ 合规未过 → 合规拒绝；
     * ④ 全过 → {@code aff_quota -= quota}（CAS）+ {@code quota += quota}（入账），原子提交。</p>
     *
     * @param userId 划转发起用户 id（认证主体注入，self-scope）
     * @param amount 划转额度
     * @throws AffQuotaTransferException   低于最小单位 / 邀请额度不足 / 合规未过
     * @throws GrowthUserNotFoundException 用户不存在/已软删除
     */
    @Transactional
    public void transfer(long userId, long amount) {
        // T5 合规前置（外部策略端口）：未过直接拒，不触碰额度。
        if (!paymentComplianceCheck.isCompliant(userId, amount)) {
            throw new AffQuotaTransferException("支付合规校验未通过");
        }

        AffiliateAccount account = affiliateAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new GrowthUserNotFoundException(userId));

        // 聚合内充血守护 T3（最小单位）+ T4（余额充足），并在内存模型上扣减（不变量自洽）。
        account.transferToQuota(amount, growthProperties.getQuotaPerUnit());

        // 持久化用 CAS 条件 UPDATE 再兜一层并发：余额不足/被并发耗尽 → 受影响 0 行 → 抛不足。
        boolean deducted = affiliateAccountRepository.deductAffQuota(userId, amount);
        if (!deducted) {
            throw new AffQuotaTransferException("邀请额度不足");
        }

        // 同事务给可用余额入账等额（GR-5 §3 quota += quota），与扣减原子提交。
        userQuotaAccount.credit(userId, amount);
    }
}
