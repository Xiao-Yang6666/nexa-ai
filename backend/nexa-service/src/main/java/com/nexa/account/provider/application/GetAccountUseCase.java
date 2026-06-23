package com.nexa.account.provider.application;

import com.nexa.account.provider.domain.exception.AccountNotFoundException;
import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;

/**
 * 供应商账号详情用例（应用层，GET /api/admin/accounts/{id}）。
 */
@Service
public class GetAccountUseCase {

    private final AccountRepository accountRepository;

    /** @param accountRepository 账号仓储 */
    public GetAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 按 id 获取账号。
     *
     * @param id 账号 id
     * @return 账号聚合
     * @throws AccountNotFoundException 账号不存在
     */
    public Account get(long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
