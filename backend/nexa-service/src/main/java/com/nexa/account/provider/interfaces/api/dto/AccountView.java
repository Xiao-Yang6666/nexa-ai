package com.nexa.account.provider.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.account.provider.domain.model.Account;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商账号管理视图 DTO（接口层，AdminAuth）。
 *
 * <p><b>凭证安全（强制）</b>：本视图<b>绝不下发 credentials 原始凭证</b>——本 DTO 不含任何 credentials
 * 字段（仿 channel key 脱敏即「不下发」）。仅含管理运维所需属性。字段名 snake_case 对齐契约。</p>
 *
 * @param id                 账号 id
 * @param name               账号名
 * @param platform           供应商平台
 * @param type               账号类型
 * @param baseUrl            上游 API base url（可空）
 * @param concurrency        并发度
 * @param priority           优先级
 * @param status             状态码（active/disabled/rate_limited）
 * @param rateLimitedAt      进入限流时刻 epoch 秒（可空）
 * @param rateLimitResetAt   限流恢复时刻 epoch 秒（可空）
 * @param overloadUntil      过载冷却截止 epoch 秒（可空）
 * @param expiresAt          过期时刻 epoch 秒（可空）
 * @param autoPauseOnExpired 过期自动暂停
 * @param rateMultiplier     账号级售价倍率
 * @param groups             所属分组集合
 * @param createdTime        创建时间 epoch 秒
 * @param updatedTime        更新时间 epoch 秒
 */
public record AccountView(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("platform") String platform,
        @JsonProperty("type") String type,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("concurrency") int concurrency,
        @JsonProperty("priority") int priority,
        @JsonProperty("status") String status,
        @JsonProperty("rate_limited_at") Long rateLimitedAt,
        @JsonProperty("rate_limit_reset_at") Long rateLimitResetAt,
        @JsonProperty("overload_until") Long overloadUntil,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("auto_pause_on_expired") boolean autoPauseOnExpired,
        @JsonProperty("rate_multiplier") BigDecimal rateMultiplier,
        @JsonProperty("groups") List<AccountGroupView> groups,
        @JsonProperty("created_time") Long createdTime,
        @JsonProperty("updated_time") Long updatedTime) {

    /**
     * 由领域聚合裁剪组装管理视图（剔除 credentials 等敏感凭证）。
     *
     * @param a 账号聚合
     * @return 管理视图 DTO（无 credentials）
     */
    public static AccountView from(Account a) {
        return new AccountView(
                a.id(), a.name(), a.platform(), a.type(), a.baseUrl(), a.concurrency(), a.priority(),
                a.status().code(), a.rateLimitedAt(), a.rateLimitResetAt(), a.overloadUntil(),
                a.expiresAt(), a.autoPauseOnExpired(), a.rateMultiplier(),
                a.groups().stream().map(AccountGroupView::from).toList(),
                a.createdTime(), a.updatedTime());
    }
}
