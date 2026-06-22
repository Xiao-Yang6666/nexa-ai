package com.nexa.token.application;

import com.nexa.token.domain.model.Token;

import java.util.List;

/**
 * 令牌分页结果（应用层读模型，承载当前页令牌 + 总数 + 分页元数据，F-3002 列表 / F-3003 搜索）。
 *
 * <p>由列表/搜索用例返回，接口层据此组装 {@code { items, total, page, page_size }} 响应
 * （对齐 openapi Pagination + TokenUserView 列表出参）。持有领域聚合列表（接口层再裁剪为
 * TokenUserView，key 脱敏、剔除敏感字段）。</p>
 *
 * @param items    当前页令牌聚合列表
 * @param total    满足条件的总条数
 * @param page     当前页码（从 1 起）
 * @param pageSize 每页条数
 */
public record TokenPage(List<Token> items, long total, int page, int pageSize) {
}
