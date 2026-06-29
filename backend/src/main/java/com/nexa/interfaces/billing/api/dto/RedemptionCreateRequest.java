package com.nexa.interfaces.billing.api.dto;

/**
 * 生成兑换码请求（管理端入参，对齐 openapi {@code RedemptionCreateRequest}）。
 *
 * <p>接口层 DTO，仅承载协议字段；校验与领域规则在用例/聚合内。{@code quota} 缺省 100、
 * {@code count} 缺省 1、{@code expiredTime} 0=不过期（缺省由用例兜底）。</p>
 *
 * @param name        名称/批次标识
 * @param quota       面额（quota 单位，缺省 100）
 * @param count       批量数量（缺省 1）
 * @param expiredTime 过期时间（epoch 秒，0=不过期）
 */
public record RedemptionCreateRequest(String name, Long quota, Integer count, Long expiredTime) {
}
