package com.nexa.model.domain.vo;

/**
 * 分页参数值对象（模型/供应商域，归一页号/每页条数）。
 *
 * <p>与 com.nexa.channel / com.nexa.token 的 Pagination 同构（各 bounded context 各自持有，
 * 避免跨 context 耦合）。归一规则：page&lt;1→1，pageSize 落在 [1,100]，缺省 page=1/pageSize=10。
 * 归一在接口层入口完成，领域/仓储拿到的恒为合法值（backend-engineer §3.4 输入校验）。</p>
 *
 * @param page     页号（从 1 起，已归一 &gt;=1）
 * @param pageSize 每页条数（已归一落 [1,100]）
 */
public record Pagination(int page, int pageSize) {

    /** 缺省每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（防一次拉爆库）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 由可空原始入参构造归一后的分页。
     *
     * @param page     原始页号（可空）
     * @param pageSize 原始每页条数（可空）
     * @return 归一后的分页
     */
    public static Pagination of(Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : pageSize;
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        return new Pagination(p, size);
    }

    /** @return JPA/SQL 偏移量（page-1）*pageSize */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
