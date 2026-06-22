package com.nexa.account.application;

/**
 * 生成 OAuth state 命令（接口层翻译后的入参，F-1015）。
 *
 * <p>对齐 openapi {@code GET /api/oauth/state} 的可选 query 参数 {@code aff}（邀请码）。</p>
 *
 * @param aff 发起授权时携带的邀请码（可空）
 */
public record GenerateOAuthStateCommand(String aff) {
}
