package com.nexa.interfaces.api.token.dto;

import com.nexa.application.token.result.TokenPage;
import com.nexa.domain.token.model.Token;

import java.util.List;

/**
 * 令牌列表视图 DTO（接口层出参，对齐 openapi Pagination + items，F-3002/F-3003）。
 *
 * <p>items 为 {@link TokenUserVO} 列表（key 脱敏），total/page/pageSize 承载分页元数据。
 * 由 {@link TokenPage}（应用层读模型）映射而来。</p>
 *
 * @param items    当前页令牌视图列表
 * @param total    满足条件的总条数
 * @param page     当前页码（从 1 起）
 * @param pageSize 每页条数
 */
public record TokenListVO(List<TokenUserVO> items, long total, int page, int pageSize) {

    /**
     * 从应用层分页结果映射为列表视图（items 内聚合→TokenUserVO）。
     *
     * @param pageResult 应用层分页结果
     * @return 列表视图 DTO
     */
    public static TokenListVO from(TokenPage pageResult) {
        List<TokenUserVO> views = pageResult.items().stream()
                .map(TokenUserVO::from)
                .toList();
        return new TokenListVO(views, pageResult.total(), pageResult.page(), pageResult.pageSize());
    }
}
