package com.nexa.interfaces.account.provider.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.application.account.provider.UpdateAccountCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编辑供应商账号请求 DTO（接口层，AdminAuth，覆盖式 PUT）。
 *
 * <p>credentials 传 null/空白表示「保留原值不变」（避免脱敏回显后回写空值清空凭证）。</p>
 *
 * @param name               账号名（必填）
 * @param platform           供应商平台（必填）
 * @param type               账号类型（必填）
 * @param credentials        凭证 JSON（可空/空白=保留原值）
 * @param baseUrl            上游 API base url（可空）
 * @param concurrency        并发度（可空→3）
 * @param priority           优先级（可空→50）
 * @param expiresAt          过期时刻 epoch 秒（可空）
 * @param autoPauseOnExpired 过期自动暂停（可空=不改）
 * @param rateMultiplier     账号级售价倍率（可空→1.0）
 * @param modelMapping       模型映射 JSON（可空）
 * @param weight             路由权重（可空→0）
 * @param tag                标签（可空）
 * @param autoBan            自动封禁标志（可空=不改）
 * @param models             支持的模型列表（可空）
 * @param groups             所属分组集合（可空）
 */
public record AccountUpdateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("platform") String platform,
        @JsonProperty("type") String type,
        @JsonProperty("credentials") String credentials,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("concurrency") Integer concurrency,
        @JsonProperty("priority") Integer priority,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("auto_pause_on_expired") Boolean autoPauseOnExpired,
        @JsonProperty("rate_multiplier") BigDecimal rateMultiplier,
        @JsonProperty("model_mapping") String modelMapping,
        @JsonProperty("weight") Integer weight,
        @JsonProperty("tag") String tag,
        @JsonProperty("auto_ban") Boolean autoBan,
        @JsonProperty("models") String models,
        @JsonProperty("groups") List<AccountGroupView> groups) {

    /**
     * 转换为编辑命令（路径 id 注入）。
     *
     * @param id 账号 id（来自路径）
     * @return 编辑命令
     */
    public UpdateAccountCommand toCommand(long id) {
        return new UpdateAccountCommand(
                id, name, platform, type, credentials, baseUrl, concurrency, priority, expiresAt,
                autoPauseOnExpired, rateMultiplier, modelMapping, weight, tag, autoBan, models,
                groups == null ? null : groups.stream().map(AccountGroupView::toDomain).toList());
    }
}
