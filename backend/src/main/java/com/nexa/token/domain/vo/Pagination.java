package com.nexa.token.domain.vo;

/**
 * 分页参数值对象（令牌列表/搜索，与 com.nexa.channel.domain.vo.Pagination 同构）。
 *
 * <p>领域规则来源：openapi PageParam/PageSizeParam（F-3002 令牌列表、F-3003 搜索）。
 * 非正/越界由工厂 {@link #of(Integer, Integer)} 归一（缺省页 1、每页 10、上限 100），不报错——
 * 分页是查询便利参数，宽容归一优于抛错（全站一致）。</p>
 *
 * @param page     页号（从 1 起，>=1）
 * @param pageSize 每页条数（1..100）
 */
public record Pagination(int page, int pageSize) {

    /** 缺省页号。 */
    public static final int DEFAULT_PAGE = 1;

    /** 缺省每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（防一次性拉全表）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 由原始（可空）入参归一构造分页。
     *
     * @param page     原始页号（可空/非正→1）
     * @param pageSize 原始每页条数（可空/非正→10；超过 100→100）
     * @return 归一后的分页值对象
     */
    public static Pagination of(Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : pageSize;
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        return new Pagination(p, size);
    }

    /**
     * @return 起始偏移量（page/pageSize 推导，供仓储分页查询）
     */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
