package com.nexa.application.account.provider;

import com.nexa.domain.account.provider.repository.AccountRepository;
import com.nexa.domain.account.provider.vo.Pagination;
import org.springframework.stereotype.Service;
import com.nexa.application.account.provider.result.AccountPage;

/**
 * 供应商账号列表查询用例（应用层，GET /api/admin/accounts）。
 *
 * <p>薄编排：归一分页 → 仓储平台过滤分页 + 计数 → 组装 {@link AccountPage}。platform 为可空过滤维度。</p>
 */
@Service
public class ListAccountsUseCase {

    private final AccountRepository accountRepository;

    /** @param accountRepository 账号仓储 */
    public ListAccountsUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 分页 + 平台过滤列出账号。
     *
     * @param platform   平台过滤（可空）
     * @param pagination 分页参数
     * @return 账号分页结果（items + total）
     */
    public AccountPage list(String platform, Pagination pagination) {
        return new AccountPage(
                accountRepository.findPage(platform, pagination),
                accountRepository.count(platform));
    }
}
