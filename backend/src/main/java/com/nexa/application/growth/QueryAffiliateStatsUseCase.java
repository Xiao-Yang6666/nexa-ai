package com.nexa.application.growth;

import com.nexa.domain.growth.exception.GrowthUserNotFoundException;
import com.nexa.domain.growth.model.AffiliateAccount;
import com.nexa.domain.growth.repository.AffiliateAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邀请统计展示用例（PRD GR-5 §5「{@code GET /self} 返回 aff_count/aff_quota/aff_history_quota 三项」，F-1045）。
 *
 * <p>只读：读取本人邀请返利账户投影聚合 {@link AffiliateAccount}，供接口层组装邀请统计三项
 * （邀请人数 / 当前可划转邀请额度 / 历史累计邀请额度）。self-scope 由接口层用认证主体注入 userId 保证。</p>
 */
@Service
public class QueryAffiliateStatsUseCase {

    private final AffiliateAccountRepository affiliateAccountRepository;

    /** @param affiliateAccountRepository 邀请返利账户仓储 */
    public QueryAffiliateStatsUseCase(AffiliateAccountRepository affiliateAccountRepository) {
        this.affiliateAccountRepository = affiliateAccountRepository;
    }

    /**
     * 读取本人邀请返利账户投影。
     *
     * @param userId 用户 id（认证主体注入，self-scope）
     * @return 邀请返利账户聚合（含 affCount/affQuota/affHistoryQuota）
     * @throws GrowthUserNotFoundException 用户不存在/已软删除
     */
    @Transactional(readOnly = true)
    public AffiliateAccount query(long userId) {
        return affiliateAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new GrowthUserNotFoundException(userId));
    }
}
