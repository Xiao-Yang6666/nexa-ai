package com.nexa.account.provider.application;

import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建供应商账号用例（应用层，POST /api/admin/accounts）。
 *
 * <p>薄编排（事务边界）：调聚合工厂 {@link Account#create} 构造合法账号（字段校验/归一在聚合内充血）
 * → 仓储保存 → 返回带 id 的聚合。</p>
 */
@Service
public class CreateAccountUseCase {

    private final AccountRepository accountRepository;

    /** @param accountRepository 账号仓储 */
    public CreateAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 创建账号。
     *
     * @param command 创建命令（name/platform/type 必填）
     * @return 持久化后的账号（含 id）
     * @throws com.nexa.account.provider.domain.exception.InvalidAccountParameterException 字段非法
     */
    @Transactional
    public Account create(CreateAccountCommand command) {
        Account account = Account.create(
                command.name(), command.platform(), command.type(), command.credentials(),
                command.baseUrl(), command.concurrency(), command.priority(), command.expiresAt(),
                command.autoPauseOnExpired(), command.rateMultiplier(),
                command.modelMapping(), command.weight(), command.tag(), command.autoBan(),
                command.models(), command.groups());
        return accountRepository.save(account);
    }
}
