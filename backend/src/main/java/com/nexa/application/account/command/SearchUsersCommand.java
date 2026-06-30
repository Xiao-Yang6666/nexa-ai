package com.nexa.application.account.command;

/**
 * 管理端用户分页搜索命令（应用层入参 DTO，F-1008）。
 *
 * <p>对齐 API-ENDPOINTS / openapi.yaml {@code GET /api/user/}（列表，无关键词）与
 * {@code GET /api/user/search}（按 keyword 搜索）。{@code keyword} 为 {@code null}/空白时
 * 退化为纯分页列表；非空时由仓储按 username/email/group 模糊匹配。</p>
 *
 * @param keyword  搜索关键词（{@code null}/空白=不过滤，纯列表）
 * @param page     页码（从 1 起；非法值由用例/仓储归一）
 * @param pageSize 每页条数（&gt;0；非法值由用例/仓储归一）
 */
public record SearchUsersCommand(String keyword, int page, int pageSize) {
}
