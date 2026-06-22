package com.nexa.shared.web;

import java.util.List;

/**
 * 全站统一分页列表响应 DTO（对齐 openapi.yaml {@code Pagination} + {@code items[]}）。
 *
 * <p>管理端列表/搜索端点的 {@code data} 载荷：分页元数据（{@code total}/{@code page}/{@code page_size}）
 * + 当页条目数组。{@code pageSize} 经全局 Jackson {@code SNAKE_CASE}（application.yml）序列化为
 * {@code page_size}，对齐 openapi {@code Pagination} schema。</p>
 *
 * <p>分页信封是协议构件（与具体条目业务语义无关），收敛到 {@code shared.web} 供全部 context 复用。</p>
 *
 * @param items    当页条目
 * @param total    匹配总条数
 * @param page     当前页码
 * @param pageSize 每页条数
 * @param <T>      条目类型
 */
public record PageView<T>(List<T> items, long total, int page, int pageSize) {
}
