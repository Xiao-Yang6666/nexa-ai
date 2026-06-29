package com.nexa.account.interfaces.api.dto;

import java.math.BigDecimal;

/**
 * 管理员充值/扣费请求（金额以 USD 计，接口层转 quota）。
 *
 * @param amount USD 金额（&gt; 0，如 10.00）
 * @param remark 备注（可空）
 */
public record AdminBalanceAdjustRequest(
        BigDecimal amount,
        String remark
) {}
