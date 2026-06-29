package com.nexa.account.provider.application;

import com.nexa.account.provider.domain.exception.AccountNotFoundException;
import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启停供应商账号用例（应用层，PATCH /api/admin/accounts/{id}/toggle）。
 *
 * <p>薄编排：按 id 加载聚合（缺失抛 404）→ 调聚合 {@link Account#enable()}/{@link Account#disable()}
 * （状态迁移走领域充血护栏）→ 仓储保存。</p>
 */
@Service
public class ToggleAccountUseCase {

    private final AccountRepository accountRepository;

    /** @param accountRepository 账号仓储 */
    public ToggleAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 启用或禁用账号。
     *
     * @param id     账号 id
     * @param enable true=启用，false=禁用
     * @return 更新后的账号
     * @throws AccountNotFoundException 账号不存在
     */
    @Transactional
    public Account toggle(long id, boolean enable) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (enable) {
            account.enable();
        } else {
            account.disable();
        }
        return accountRepository.save(account);
    }
}
