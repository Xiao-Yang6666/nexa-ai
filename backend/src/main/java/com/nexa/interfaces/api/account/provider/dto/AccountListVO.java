package com.nexa.interfaces.api.account.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.account.provider.AccountPage;

import java.util.List;

/**
 * 供应商账号列表视图 DTO（接口层，{@code { items, total }}，AdminAuth）。
 *
 * <p>由分页结果裁剪为 AccountVO 列表（剔除 credentials 等敏感字段）。</p>
 *
 * @param items 当前页账号视图列表
 * @param total 满足条件的总条数
 */
public record AccountListVO(
        @JsonProperty("items") List<AccountVO> items,
        @JsonProperty("total") long total) {

    /**
     * 由应用层分页结果组装列表视图。
     *
     * @param page 账号分页结果
     * @return 列表视图 DTO
     */
    public static AccountListVO from(AccountPage page) {
        return new AccountListVO(
                page.items().stream().map(AccountVO::from).toList(),
                page.total());
    }
}
