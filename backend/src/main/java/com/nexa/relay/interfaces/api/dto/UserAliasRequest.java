package com.nexa.relay.interfaces.api.dto;

/**
 * 客户层别名创建请求 DTO（L1 C→A，用户自助，F-6011）。
 *
 * <p>越权护栏：scope 由控制器从 @CurrentActor 注入（强制本人 user_id / 本人 group），
 * 请求体不含 scope（防客户跨 scope 写，对齐 ARCHITECTURE-REVIEW §6 self-scope）。
 * {@code target}(A) 不强制白名单（ADR-COMPAT-06）。</p>
 *
 * @param alias  C 客户别名
 * @param target A 目标公开名（不强制白名单）
 */
public record UserAliasRequest(String alias, String target) {
}
