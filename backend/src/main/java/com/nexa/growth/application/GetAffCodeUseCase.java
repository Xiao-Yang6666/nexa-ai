package com.nexa.growth.application;

import com.nexa.growth.domain.repository.AffiliateAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 获取个人邀请码用例（PRD GR-4 «生成邀请码»，F-1039，{@code GET /api/user/self/aff}）。
 *
 * <p>薄编排：委托仓储 {@link AffiliateAccountRepository#getOrCreateAffCode}「有则返回、无则生成唯一 4 位
 * 码并落库」（GR-4 I1/I2/I3，唯一性由 {@code idx_users_aff_code} 兜底）。「生成唯一码需查重（IO）」
 * 属仓储职责，故放仓储而非聚合（聚合保持纯内存可单测，backend-engineer §2.3）。</p>
 */
@Service
public class GetAffCodeUseCase {

    private final AffiliateAccountRepository affiliateAccountRepository;

    /** @param affiliateAccountRepository 邀请返利账户仓储 */
    public GetAffCodeUseCase(AffiliateAccountRepository affiliateAccountRepository) {
        this.affiliateAccountRepository = affiliateAccountRepository;
    }

    /**
     * 取本人邀请码（无则生成）。
     *
     * @param userId 用户 id（认证主体注入，self-scope）
     * @return 该用户的 4 位邀请码（首次进入生成并落库，再次进入返回同一码）
     */
    @Transactional
    public String getOrCreate(long userId) {
        return affiliateAccountRepository.getOrCreateAffCode(userId);
    }
}
