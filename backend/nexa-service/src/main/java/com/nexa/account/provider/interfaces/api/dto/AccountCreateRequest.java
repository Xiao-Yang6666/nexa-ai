package com.nexa.account.provider.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.account.provider.application.CreateAccountCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建供应商账号请求 DTO（接口层，AdminAuth）。
 *
 * <p>接口层只做协议绑定 + 透传到应用命令，校验/归一在领域聚合（充血）。
 * credentials 为敏感凭证 JSON 串。</p>
 *
 * @param name               账号名（必填，由聚合校验）
 * @param platform           供应商平台（必填）
 * @param type               账号类型（必填）
 * @param credentials        凭证 JSON（敏感，可空）
 * @param baseUrl            上游 API base url（可空）
 * @param concurrency        并发度（可空→3）
 * @param priority           优先级（可空→50）
 * @param expiresAt          过期时刻 epoch 秒（可空）
 * @param autoPauseOnExpired 过期自动暂停（可空→true）
 * @param rateMultiplier     账号级售价倍率（可空→1.0）
 * @param modelMapping       模型映射 JSON（可空）
 * @param weight             路由权重（可空→0）
 * @param tag                标签（可空）
 * @param autoBan            自动封禁标志（可空→false）
 * @param models             支持的模型列表（可空）
 * @param groups             所属分组集合（可空）
 */
public record AccountCreateRequest(
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
     * 转换为创建命令（分组视图转领域值对象）。
     *
     * @return 创建命令
     */
    public CreateAccountCommand toCommand() {
        return new CreateAccountCommand(
                name, platform, type, credentials, baseUrl, concurrency, priority, expiresAt,
                autoPauseOnExpired, rateMultiplier, modelMapping, weight, tag, autoBan, models,
                groups == null ? null : groups.stream().map(AccountGroupView::toDomain).toList());
    }
}
