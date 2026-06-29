package com.nexa.domain.deployment.vo;

/**
 * 分页参数值对象（部署列表查询入参，F-3041）。
 *
 * <p>不可变、按值相等。归一「部署列表查询」（API-ENDPOINTS §10.2 GET /api/deployments/）的分页入参
 * {@code p}/{@code page_size}：契约对分页参数取宽松默认（非正回退默认值、上限封顶），不报错。把这条归一规则
 * 固化在值对象，避免负数/超大页号穿透到上游（backend-engineer §2.4 值对象守护不变量）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §10.2 F-3041（分页 + 状态计数）。默认页号 1、默认页大小 10、
 * 页大小上限 100（防止单页拉取过多拖垮上游/响应，对齐 NFR 合理分页约束）。</p>
 *
 * @param page     页号（保证 &gt;= 1）
 * @param pageSize 每页条数（保证 1..100）
 */
public record Pagination(int page, int pageSize) {

    /** 默认页号（缺省/非正时回退）。 */
    public static final int DEFAULT_PAGE = 1;

    /** 默认每页条数（缺省/非正时回退）。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（防止单页过大）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 紧凑构造器：构造即归一（非正回退默认、页大小封顶）。
     *
     * <p>不抛错——契约对分页越界取宽松回退而非拒绝（与 F-3056 limit 截断同风格）。</p>
     */
    public Pagination {
        if (page < 1) {
            page = DEFAULT_PAGE;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            // 超上限封顶（不报错），避免单页拉取过多。
            pageSize = MAX_PAGE_SIZE;
        }
    }

    /**
     * 从 query 原始参数归一构造（null/非正按默认回退）。
     *
     * @param page     query 参数 {@code p}（可空）
     * @param pageSize query 参数 {@code page_size}（可空）
     * @return 归一后的分页参数
     */
    public static Pagination of(Integer page, Integer pageSize) {
        int p = page == null ? DEFAULT_PAGE : page;
        int ps = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return new Pagination(p, ps);
    }
}
