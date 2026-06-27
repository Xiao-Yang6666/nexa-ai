package com.nexa.account.provider.application;

import com.nexa.account.provider.domain.exception.AccountNotFoundException;
import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编辑供应商账号用例（应用层，PUT /api/admin/accounts，覆盖式）。
 *
 * <p>薄编排：按 id 加载聚合（缺失抛 404）→ 调聚合 {@link Account#update}（校验/归一/凭证保留在充血内）
 * → 仓储保存。credentials 空白表示保留原值。</p>
 */
@Service
public class UpdateAccountUseCase {

    private final AccountRepository accountRepository;

    /** @param accountRepository 账号仓储 */
    public UpdateAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 编辑账号。
     *
     * @param command 编辑命令（id/name/platform/type 必填）
     * @return 更新后的账号
     * @throws AccountNotFoundException 账号不存在
     * @throws com.nexa.account.provider.domain.exception.InvalidAccountParameterException 字段非法
     */
    @Transactional
    public Account update(UpdateAccountCommand command) {
        Account account = accountRepository.findById(command.id())
                .orElseThrow(() -> new AccountNotFoundException(command.id()));
        account.update(command.name(), command.platform(), command.type(), command.credentials(),
                command.baseUrl(), command.concurrency(), command.priority(), command.expiresAt(),
                command.autoPauseOnExpired(), command.rateMultiplier(),
                command.modelMapping(), command.weight(), command.tag(), command.autoBan(),
                command.models(), command.groups());
        return accountRepository.save(account);
    }
}
