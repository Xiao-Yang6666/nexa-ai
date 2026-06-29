package com.nexa.application.billing;

/**
 * 兑换码生成命令（应用层入参，prd-billing BL-4 §5，对齐 openapi RedemptionCreateRequest）。
 *
 * <p>接口层把 HTTP 请求 DTO 翻译为本命令，传给 {@link GenerateRedemptionsUseCase}。
 * 字段可空由用例兜底（quota 缺省 100、count 缺省 1、expiredTime 缺省 0=不过期）。</p>
 *
 * @param name        名称/批次标识（可空）
 * @param quota       面额（quota 单位，可空→缺省 100）
 * @param count       批量数量（可空→缺省 1）
 * @param expiredTime 过期时间（epoch 秒，可空/0=不过期）
 */
public record GenerateRedemptionsCommand(String name, Long quota, Integer count, Long expiredTime) {
}
