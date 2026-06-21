package com.nexa.billing.interfaces.api.dto;

import java.util.List;

/**
 * 分页视图（接口层出参，对齐 openapi {@code Pagination}：items + total + page + page_size）。
 *
 * <p>计费域分页端点（兑换码列表等）统一用本 DTO 承载分页元数据。与各 bounded context 的
 * PageView 同构（接口层各自持有，不跨 context 复用）。</p>
 *
 * @param items    当页数据视图列表
 * @param total    匹配总条数（前端算总页数）
 * @param page     当前页码（从 1 起）
 * @param pageSize 每页条数
 * @param <T>      视图类型
 */
public record PageView<T>(List<T> items, long total, int page, int pageSize) {
}
