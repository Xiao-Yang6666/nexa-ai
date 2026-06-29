package com.nexa.application.growth;

import com.nexa.domain.growth.repository.AffiliateAccountRepository;
import com.nexa.infrastructure.growth.config.GrowthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邀请返利入账用例（PRD GR-4 «返利入账» 回调侧，F-1042/F-1043）。
 *
 * <p>被邀请人成功归因（新用户 {@code inviter_id = 邀请人 Id} 且有效）后，对邀请人入账返利：
 * 邀请人数 +1、当前邀请额度与历史累计邀请额度各 += {@code QuotaForInviter}（GR-4 I13/I14）。仅在
 * inviterId 有效（&gt; 0）时执行；无效/空归因（GR-4 I10-否，inviterId=0）由调用方跳过本用例（无返利态）。</p>
 *
 * <p>原子入账：仓储 {@link AffiliateAccountRepository#creditInviterReward} 用 SQL 级原子自增落库，
 * 杜绝并发归因丢更新（多被邀请人同时注册命中同一邀请人）。{@code @Transactional} 包裹保证一致。
 * 入账目标用户不存在/已删时仓储返回 false，本用例静默放过（不让一次失败的归因阻断被邀请人注册主流程）。</p>
 */
@Service
public class CreditInviterRewardUseCase {

    private final AffiliateAccountRepository affiliateAccountRepository;
    private final GrowthProperties growthProperties;

    /**
     * @param affiliateAccountRepository 邀请返利账户仓储（原子入账）
     * @param growthProperties           增长常量（提供 QuotaForInviter 单次返利额度）
     */
    public CreditInviterRewardUseCase(AffiliateAccountRepository affiliateAccountRepository,
                                      GrowthProperties growthProperties) {
        this.affiliateAccountRepository = affiliateAccountRepository;
        this.growthProperties = growthProperties;
    }

    /**
     * 对邀请人入账一份返利（GR-4 I13/I14）。
     *
     * <p>仅当 {@code inviterId > 0} 时执行（有效归因）；否则直接返回 {@code false}（无归因，无返利态，
     * GR-4 I10-否分支）。返利额度取配置 {@code QuotaForInviter}。</p>
     *
     * @param inviterId 邀请人 id（无效/空归因传 0 或负）
     * @return 实际入账成功返回 {@code true}；无效归因或目标用户缺失返回 {@code false}
     */
    @Transactional
    public boolean creditFor(long inviterId) {
        if (inviterId <= 0) {
            return false; // 无效/空 aff_code → InviterId=0，不发返利（GR-4 无归因态）
        }
        long reward = growthProperties.getQuotaForInviter();
        return affiliateAccountRepository.creditInviterReward(inviterId, reward);
    }
}
