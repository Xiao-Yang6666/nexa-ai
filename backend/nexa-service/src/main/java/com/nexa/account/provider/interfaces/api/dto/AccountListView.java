package com.nexa.account.provider.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.account.provider.application.AccountPage;

import java.util.List;

/**
 * 供应商账号列表视图 DTO（接口层，{@code { items, total }}，AdminAuth）。
 *
 * <p>由分页结果裁剪为 AccountView 列表（剔除 credentials 等敏感字段）。</p>
 *
 * @param items 当前页账号视图列表
 * @param total 满足条件的总条数
 */
public record AccountListView(
        @JsonProperty("items") List<AccountView> items,
        @JsonProperty("total") long total) {

    /**
     * 由应用层分页结果组装列表视图。
     *
     * @param page 账号分页结果
     * @return 列表视图 DTO
     */
    public static AccountListView from(AccountPage page) {
        return new AccountListView(
                page.items().stream().map(AccountView::from).toList(),
                page.total());
    }
}
