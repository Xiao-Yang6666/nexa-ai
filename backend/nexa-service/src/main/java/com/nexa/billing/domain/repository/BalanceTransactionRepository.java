package com.nexa.billing.domain.repository;

import com.nexa.billing.domain.model.BalanceTransaction;

import java.util.List;

/**
 * 账变流水仓储（domain 定接口，infrastructure 实现）。
 */
public interface BalanceTransactionRepository {

    /**
     * 保存一条账变记录（新增），回填自增 id。
     *
     * @param tx 待保存账变
     * @return 持久化后的账变（含 id）
     */
    BalanceTransaction save(BalanceTransaction tx);

    /**
     * 按用户查账变流水（按时间倒序，最多 limit 条）。
     *
     * @param userId 用户 id
     * @param limit  返回上限
     * @return 该用户账变流水（时间倒序）
     */
    List<BalanceTransaction> findByUser(long userId, int limit);
}
