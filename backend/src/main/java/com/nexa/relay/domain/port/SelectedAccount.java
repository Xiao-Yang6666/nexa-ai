package com.nexa.relay.domain.port;

import java.math.BigDecimal;

/**
 * 选中的供应商账号凭证投影（relay 域端口出参，account BC → relay 的防腐层）。
 *
 * <p>relay 转发链选 account 后只需要这几样：凭证(credentials)、平台(platform，决定上游协议/baseUrl)、
 * accountId(转发失败回写限流/过载用)、账号级倍率(rateMultiplier，阶段5 计费)。本 VO 把 account 聚合
 * 裁剪为 relay 所需最小投影，relay 域不编译期耦合 account.provider 内部聚合。</p>
 *
 * <p>credentials 为敏感凭证 JSON：仅在 relay 转发链内部短暂使用（取出发往上游），绝不落 relay 日志、
 * 绝不下发任何视图。</p>
 *
 * @param accountId      账号 id（转发失败回写限流/过载状态用）
 * @param credentials    凭证 JSON（敏感，发往上游用）
 * @param baseUrl        上游 API base url（可空；空则转发回落 channel.baseUrl）
 * @param platform       供应商平台（openai/anthropic 等，决定上游协议与 baseUrl）
 * @param rateMultiplier 账号级售价倍率（>=0，阶段5 计费用）
 */
public record SelectedAccount(
        long accountId,
        String credentials,
        String baseUrl,
        String platform,
        BigDecimal rateMultiplier,
        String modelMapping,
        String models,
        String tag,
        int weight) {

    /**
     * 应用账号级模型映射 A→B。
     * 无映射或解析失败返回原值。
     *
     * @param publicModel 公开模型名 A
     * @return 上游真实模型名 B（未映射则返回 A）
     */
    public String applyModelMapping(String publicModel) {
        if (modelMapping == null || modelMapping.isBlank()) {
            return publicModel;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(modelMapping)
                            .path(publicModel);
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        } catch (Exception ignored) {
        }
        return publicModel;
    }
}
