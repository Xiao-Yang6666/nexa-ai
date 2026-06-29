package com.nexa.application.account.provider;

import com.nexa.domain.account.provider.model.Account;

import java.util.List;

/**
 * 供应商账号分页结果（应用层读模型，承载当前页账号 + 总数）。
 *
 * <p>持有领域聚合列表（接口层再裁剪为 AccountVO，剔除 credentials 等敏感字段）。</p>
 *
 * @param items 当前页账号聚合列表
 * @param total 满足条件的总条数
 */
public record AccountPage(List<Account> items, long total) {
}
