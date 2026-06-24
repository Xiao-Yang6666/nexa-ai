package com.nexa.account.provider.application;

import com.nexa.account.provider.domain.vo.AccountGroupRef;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建供应商账号命令（应用层入参 DTO，接口层 → 用例透传，校验/归一在领域聚合）。
 *
 * @param name               账号名（必填）
 * @param platform           供应商平台（必填）
 * @param type               账号类型（必填）
 * @param credentials        凭证 JSON（敏感，可空）
 * @param baseUrl            上游 API base url（可空）
 * @param concurrency        并发度（可空→3）
 * @param priority           优先级（可空→50）
 * @param expiresAt          过期时刻 epoch 秒（可空）
 * @param autoPauseOnExpired 过期自动暂停（可空→true）
 * @param rateMultiplier     账号级售价倍率（可空→1.0）
 * @param groups             所属分组集合（可空）
 */
public record CreateAccountCommand(
        String name,
        String platform,
        String type,
        String credentials,
        String baseUrl,
        Integer concurrency,
        Integer priority,
        Long expiresAt,
        Boolean autoPauseOnExpired,
        BigDecimal rateMultiplier,
        List<AccountGroupRef> groups) {
}
