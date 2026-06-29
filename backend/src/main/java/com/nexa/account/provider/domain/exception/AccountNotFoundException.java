package com.nexa.account.provider.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 供应商账号不存在异常（按 id 操作但账号缺失，→404）。
 *
 * <p>详情/编辑/删除/启停均按 id 定位账号，命中失败抛本异常，接口层翻译为 404 NotFound。</p>
 */
public class AccountNotFoundException extends DomainException {

    /** @param id 缺失的账号 id */
    public AccountNotFoundException(long id) {
        super("ACCOUNT_NOT_FOUND", "provider account not found: " + id);
    }
}
