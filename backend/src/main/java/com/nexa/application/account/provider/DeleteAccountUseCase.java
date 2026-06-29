package com.nexa.application.account.provider;

import com.nexa.domain.account.provider.exception.AccountNotFoundException;
import com.nexa.domain.account.provider.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除供应商账号用例（应用层，DELETE /api/admin/accounts/{id}）。
 *
 * <p>删除前校验存在性（缺失抛 404），仓储连带清理 account_groups 关联。</p>
 */
@Service
public class DeleteAccountUseCase {

    private final AccountRepository accountRepository;

    /** @param accountRepository 账号仓储 */
    public DeleteAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 删除账号。
     *
     * @param id 账号 id
     * @throws AccountNotFoundException 账号不存在
     */
    @Transactional
    public void delete(long id) {
        if (accountRepository.findById(id).isEmpty()) {
            throw new AccountNotFoundException(id);
        }
        accountRepository.deleteById(id);
    }
}
