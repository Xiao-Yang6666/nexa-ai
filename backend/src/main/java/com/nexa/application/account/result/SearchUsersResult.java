package com.nexa.application.account.result;

import com.nexa.domain.account.model.User;

import java.util.List;

/**
 * 管理端用户搜索结果（应用层出参，F-1008）。
 *
 * <p>承载当页用户聚合 + 分页元数据，供接口层投影为 {@code UserAdminView} 列表 + {@code Pagination}
 * （openapi.yaml {@code GET /api/user/} 200 = {@code Pagination + items[]}）。应用层只回领域聚合，
 * 由接口层裁剪敏感字段（passwordHash 绝不下发，产品铁律）。</p>
 *
 * @param users    当页用户聚合列表
 * @param total    匹配总条数（前端据此算总页数）
 * @param page     当前页码
 * @param pageSize 每页条数
 */
public record SearchUsersResult(List<User> users, long total, int page, int pageSize) {
}
